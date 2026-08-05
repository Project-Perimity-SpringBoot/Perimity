package com.perimity.user.service;

import com.perimity.user.client.PassPauseClient;
import com.perimity.user.dto.request.FacultyProfileCreateDto;
import com.perimity.user.dto.request.FacultyProfileUpdateDto;
import com.perimity.user.dto.response.FacultyProfileResponse;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty identity profiles.
 *
 * Structurally the same as StudentProfileService, and deliberately NOT merged
 * with it. The two entities are separate tables that share almost no fields
 * (roll number and government id versus employee id, designation and
 * qualification), and a generic "profile service" over both would spend most of
 * its body branching on which kind it was holding.
 *
 * The two rules that do carry over are identical and matter:
 *
 *   - on update, null means "leave alone" and "" means "clear". A form that
 *     posts only a designation must not wipe the employee id.
 *   - employeeId and photoS3Key are SENSITIVE. Changing either makes the pass
 *     in the holder's pocket describe someone who no longer matches, so every
 *     active pass is held for re-approval.
 *
 * Faculty carry no government id here. Their identity documents go through
 * DocumentService like anyone else's.
 */
@Service
public class FacultyProfileService {

    private static final Logger log = LoggerFactory.getLogger(FacultyProfileService.class);

    private final FacultyProfileRepository facultyRepository;
    private final ProfileGuard guard;
    private final PassPauseClient passPauseClient;
    private final CurrentUser currentUser;

    public FacultyProfileService(FacultyProfileRepository facultyRepository,
                                 ProfileGuard guard,
                                 PassPauseClient passPauseClient,
                                 CurrentUser currentUser) {
        this.facultyRepository = facultyRepository;
        this.guard = guard;
        this.passPauseClient = passPauseClient;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ create

    /**
     * CREATE-OR-FILL. See StudentProfileService.create for the full reasoning.
     *
     * In short: a user.created event provisions an empty profile the moment the
     * account is made, so a caller creating one explicitly usually finds a row
     * already there, and which arrives first is a race between a queue and an
     * HTTP round trip. Refusing on the losing side would fail intermittently.
     */
    @Transactional
    public FacultyProfileResponse create(FacultyProfileCreateDto dto) {
        currentUser.requireSameCampus(dto.getCampusId());

        String employeeId = trimToNull(dto.getEmployeeId());
        guard.requireSelectableDepartment(dto.getCampusId(), dto.getDepartmentId());

        Optional<FacultyProfile> existing = facultyRepository.findByUserId(dto.getUserId());
        if (existing.isPresent()) {
            FacultyProfile profile = existing.get();
            requireVisible(profile);
            requireEmployeeIdAvailable(dto.getCampusId(), employeeId, profile.getId());

            // Only overwrite what the caller actually supplied. A null here
            // means "not my business", not "clear it".
            if (employeeId != null) {
                profile.setEmployeeId(employeeId);
            }
            if (dto.getDepartmentId() != null) {
                profile.setDepartmentId(dto.getDepartmentId());
            }
            if (trimToNull(dto.getDesignation()) != null) {
                profile.setDesignation(trimToNull(dto.getDesignation()));
            }
            if (trimToNull(dto.getQualification()) != null) {
                profile.setQualification(trimToNull(dto.getQualification()));
            }
            if (trimToNull(dto.getPhotoS3Key()) != null) {
                profile.setPhotoS3Key(trimToNull(dto.getPhotoS3Key()));
            }

            FacultyProfile saved = facultyRepository.save(profile);
            log.info("Filled in auto-provisioned faculty profile {} for account {}.",
                    saved.getId(), saved.getUserId());
            return FacultyProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
        }

        requireEmployeeIdAvailable(dto.getCampusId(), employeeId, null);

        FacultyProfile profile = FacultyProfile.builder()
                .userId(dto.getUserId())
                .campusId(dto.getCampusId())
                .departmentId(dto.getDepartmentId())
                .employeeId(employeeId)
                .designation(trimToNull(dto.getDesignation()))
                .qualification(trimToNull(dto.getQualification()))
                .photoS3Key(trimToNull(dto.getPhotoS3Key()))
                .build();

        FacultyProfile saved = facultyRepository.save(profile);
        return FacultyProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // -------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public FacultyProfileResponse getOne(Long id) {
        FacultyProfile profile = requireVisible(id);
        currentUser.requireSelfOrStaff(profile.getUserId());
        return FacultyProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    @Transactional(readOnly = true)
    public FacultyProfileResponse getByUserId(Long userId) {
        currentUser.requireSelfOrStaff(userId);

        FacultyProfile profile = facultyRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No faculty profile exists for account " + userId));

        requireVisible(profile);
        return FacultyProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    /** The faculty directory - the list a visitor picks a host from. */
    @Transactional(readOnly = true)
    public PageResponse<FacultyProfileResponse> list(Long campusId, Long departmentId, Pageable pageable) {
        var page = departmentId == null
                ? facultyRepository.findByCampusIdOrderByIdDesc(campusId, pageable)
                : facultyRepository.findByCampusIdAndDepartmentIdOrderByIdDesc(
                        campusId, departmentId, pageable);

        return PageResponse.from(page, FacultyProfileResponse::from);
    }

    @Transactional(readOnly = true)
    public long countByCampus(Long campusId) {
        return facultyRepository.countByCampusId(campusId);
    }

    // ------------------------------------------------------------ update

    @Transactional
    public FacultyProfileResponse update(Long id, FacultyProfileUpdateDto dto) {
        FacultyProfile profile = requireVisible(id);
        currentUser.requireSelfOrStaff(profile.getUserId());

        List<String> sensitiveChanges = new ArrayList<>();

        if (dto.getEmployeeId() != null) {
            String employeeId = trimToNull(dto.getEmployeeId());
            if (!Objects.equals(employeeId, profile.getEmployeeId())) {
                requireEmployeeIdAvailable(profile.getCampusId(), employeeId, profile.getId());
                profile.setEmployeeId(employeeId);
                sensitiveChanges.add("employee ID");
            }
        }

        if (dto.getPhotoS3Key() != null) {
            String photo = trimToNull(dto.getPhotoS3Key());
            if (!Objects.equals(photo, profile.getPhotoS3Key())) {
                profile.setPhotoS3Key(photo);
                sensitiveChanges.add("photo");
            }
        }

        if (dto.getDepartmentId() != null
                && !dto.getDepartmentId().equals(profile.getDepartmentId())) {
            guard.requireSelectableDepartment(profile.getCampusId(), dto.getDepartmentId());
            profile.setDepartmentId(dto.getDepartmentId());
        }

        if (dto.getDesignation() != null) {
            profile.setDesignation(trimToNull(dto.getDesignation()));
        }

        if (dto.getQualification() != null) {
            profile.setQualification(trimToNull(dto.getQualification()));
        }

        FacultyProfile saved = facultyRepository.save(profile);

        if (!sensitiveChanges.isEmpty()) {
            String reason = "Profile change requires re-approval: " + String.join(", ", sensitiveChanges);
            boolean paused = passPauseClient.pauseAllForHolder(
                    saved.getUserId(), reason, currentUser.userId());
            if (!paused) {
                log.warn("Sensitive change on faculty account {} ({}) but no pass was paused.",
                        saved.getUserId(), reason);
            }
        }

        return FacultyProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // ----------------------------------------------------------- helpers

    private void requireEmployeeIdAvailable(Long campusId, String employeeId, Long selfId) {
        if (employeeId == null) {
            return;
        }
        Optional<FacultyProfile> holder =
                facultyRepository.findByCampusIdAndEmployeeIdIgnoreCase(campusId, employeeId);

        if (holder.isPresent() && !holder.get().getId().equals(selfId)) {
            throw new IllegalStateException(
                    "Employee ID \"" + employeeId + "\" is already used on this campus.");
        }
    }

    /** Another campus's profile reads as "not found", never as "forbidden". */
    private FacultyProfile requireVisible(Long id) {
        FacultyProfile profile = facultyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Faculty profile", id));
        return requireVisible(profile);
    }

    private FacultyProfile requireVisible(FacultyProfile profile) {
        if (!currentUser.canSeeCampus(profile.getCampusId())) {
            throw ResourceNotFoundException.of("Faculty profile", profile.getId());
        }
        return profile;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
