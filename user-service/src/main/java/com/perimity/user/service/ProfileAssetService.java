package com.perimity.user.service;

import com.perimity.user.client.PassPauseClient;
import com.perimity.user.dto.response.FacultyProfileResponse;
import com.perimity.user.dto.response.PresignedUrlResponse;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.storage.StorageException;
import com.perimity.user.storage.StorageKeys;
import com.perimity.user.storage.StorageService;
import com.perimity.user.storage.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Profile photo upload, replacement and removal (Day 9).
 *
 * Kept out of StudentProfileService and FacultyProfileService on purpose. Those
 * two are about identity data and know nothing about object storage; folding
 * uploads into them would make every profile edit depend on a storage bean and
 * would double the size of both classes for one feature.
 *
 * ==================================================
 *  A PHOTO CHANGE PAUSES THE PASS. That is the point.
 * ==================================================
 *
 * The photo is what a guard checks a face against. Changing it while a pass is
 * ACTIVE means the QR in someone's pocket now vouches for a different picture,
 * which is exactly the hole SRS v1.1 closes. So an upload here goes through the
 * same PassPauseClient as a roll-number edit.
 *
 * ORDER OF OPERATIONS, and why it is not the obvious one:
 *   1. validate     - cheapest, and rejects before anything is written
 *   2. store the NEW object
 *   3. save the row
 *   4. delete the OLD object
 *   5. pause the pass
 *
 * Deleting first would be tidier and is wrong: if the upload then failed the
 * person would be left with no photo at all. Pausing last means a storage
 * failure never suspends somebody's access for an edit that did not happen.
 */
@Service
public class ProfileAssetService {

    private static final Logger log = LoggerFactory.getLogger(ProfileAssetService.class);
    private static final String PAUSE_REASON = "Profile change requires re-approval: photo";

    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final ProfileGuard guard;
    private final PassPauseClient passPauseClient;
    private final CurrentUser currentUser;

    private final long maxPhotoBytes;
    private final int presignMinutes;

    public ProfileAssetService(StudentProfileRepository studentRepository,
                               FacultyProfileRepository facultyRepository,
                               StorageService storage,
                               UploadValidator uploadValidator,
                               ProfileGuard guard,
                               PassPauseClient passPauseClient,
                               CurrentUser currentUser,
                               @Value("${perimity.storage.max-photo-mb}") long maxPhotoMb,
                               @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.storage = storage;
        this.uploadValidator = uploadValidator;
        this.guard = guard;
        this.passPauseClient = passPauseClient;
        this.currentUser = currentUser;
        this.maxPhotoBytes = maxPhotoMb * 1024 * 1024;
        this.presignMinutes = presignMinutes;
    }

    // ------------------------------------------------------------ student

    @Transactional
    public StudentProfileResponse uploadStudentPhoto(Long profileId, MultipartFile file) {
        StudentProfile profile = requireVisibleStudent(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());

        String contentType = uploadValidator.validatePhoto(file, maxPhotoBytes);
        String key = StorageKeys.studentPhoto(
                profile.getCampusId(), profile.getUserId(), file.getOriginalFilename());

        String previousKey = profile.getPhotoS3Key();
        profile.setPhotoS3Key(store(key, file, contentType).key());
        StudentProfile saved = studentRepository.save(profile);

        replaceOldObject(previousKey, saved.getPhotoS3Key());
        pause(saved.getUserId());

        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse studentPhotoUrl(Long profileId) {
        StudentProfile profile = requireVisibleStudent(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());
        return urlFor(profile.getPhotoS3Key(), "student profile " + profileId);
    }

    @Transactional
    public StudentProfileResponse removeStudentPhoto(Long profileId) {
        StudentProfile profile = requireVisibleStudent(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());

        String previousKey = profile.getPhotoS3Key();
        if (previousKey != null) {
            profile.setPhotoS3Key(null);
            studentRepository.save(profile);
            storage.delete(previousKey);
            // Removing the photo is as much of a change as replacing it - the
            // pass still shows a face nobody can now check against.
            pause(profile.getUserId());
        }
        return StudentProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    // ------------------------------------------------------------ faculty

    @Transactional
    public FacultyProfileResponse uploadFacultyPhoto(Long profileId, MultipartFile file) {
        FacultyProfile profile = requireVisibleFaculty(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());

        String contentType = uploadValidator.validatePhoto(file, maxPhotoBytes);
        String key = StorageKeys.facultyPhoto(
                profile.getCampusId(), profile.getUserId(), file.getOriginalFilename());

        String previousKey = profile.getPhotoS3Key();
        profile.setPhotoS3Key(store(key, file, contentType).key());
        FacultyProfile saved = facultyRepository.save(profile);

        replaceOldObject(previousKey, saved.getPhotoS3Key());
        pause(saved.getUserId());

        return FacultyProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse facultyPhotoUrl(Long profileId) {
        FacultyProfile profile = requireVisibleFaculty(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());
        return urlFor(profile.getPhotoS3Key(), "faculty profile " + profileId);
    }

    @Transactional
    public FacultyProfileResponse removeFacultyPhoto(Long profileId) {
        FacultyProfile profile = requireVisibleFaculty(profileId);
        currentUser.requireSelfOrStaff(profile.getUserId());

        String previousKey = profile.getPhotoS3Key();
        if (previousKey != null) {
            profile.setPhotoS3Key(null);
            facultyRepository.save(profile);
            storage.delete(previousKey);
            pause(profile.getUserId());
        }
        return FacultyProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    // ----------------------------------------------------------- helpers

    private StoredObject store(String key, MultipartFile file, String contentType) {
        try (InputStream in = file.getInputStream()) {
            return storage.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded file", e);
        }
    }

    /** Only after the new object is safely saved, and never the same key twice. */
    private void replaceOldObject(String previousKey, String newKey) {
        if (previousKey != null && !previousKey.equals(newKey)) {
            storage.delete(previousKey);
            log.info("Replaced photo, removed {}", previousKey);
        }
    }

    private void pause(Long holderUserId) {
        boolean paused = passPauseClient.pauseAllForHolder(
                holderUserId, PAUSE_REASON, currentUser.userId());
        if (!paused) {
            log.warn("Photo changed for account {} but no pass was paused.", holderUserId);
        }
    }

    private PresignedUrlResponse urlFor(String key, String what) {
        if (key == null) {
            throw new ResourceNotFoundException("There is no photo on " + what + ".");
        }
        return PresignedUrlResponse.of(
                storage.presignedReadUrl(key, Duration.ofMinutes(presignMinutes)), presignMinutes);
    }

    /** Another campus's profile reads as "not found", never as "forbidden". */
    private StudentProfile requireVisibleStudent(Long id) {
        StudentProfile profile = studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student profile", id));
        if (!currentUser.canSeeCampus(profile.getCampusId())) {
            throw ResourceNotFoundException.of("Student profile", id);
        }
        return profile;
    }

    private FacultyProfile requireVisibleFaculty(Long id) {
        FacultyProfile profile = facultyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Faculty profile", id));
        if (!currentUser.canSeeCampus(profile.getCampusId())) {
            throw ResourceNotFoundException.of("Faculty profile", id);
        }
        return profile;
    }
}
