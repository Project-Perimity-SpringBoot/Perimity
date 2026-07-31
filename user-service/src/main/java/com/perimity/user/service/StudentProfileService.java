package com.perimity.user.service;

import com.perimity.user.client.PassPauseClient;
import com.perimity.user.dto.request.StudentProfileCreateDto;
import com.perimity.user.dto.request.StudentProfileUpdateDto;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.StudentProfileRepository;
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
 * Student identity profiles.
 *
 * Division of labour with the DTO layer, same as DepartmentService:
 * StudentProfileCreateDto and StudentProfileUpdateDto have already proved the
 * input is well formed - roll number shape, twelve-digit government id, object
 * key with no path traversal. Everything here needs the database or the current
 * state of a row: does this account already have a profile, is that roll number
 * taken on this campus, does the named department exist and is it still active,
 * and - the one that matters most - did a sensitive field just change.
 *
 * ==================================================================
 *  THE UPDATE CONTRACT: null means "leave it alone", "" means "clear"
 * ==================================================================
 *
 * Every field on the update DTO is optional. Treating a missing field as
 * "set this to null" would let a form that only sends the address silently
 * erase the student's roll number and photo - and it would pause their pass
 * while doing it. So null is ignored and an empty string is the explicit way to
 * clear a value. The government id pattern (^$|^\d{12}$) allows the empty
 * string on purpose, which is what settled the question.
 *
 * =========================================================
 *  THE PAUSE RULE (SRS v1.1) - why it is here and not in the DTO
 * =========================================================
 *
 * Changing rollNo, govId or photoS3Key makes the pass in the holder's pocket
 * describe someone who no longer matches the record. A changed photo on a still
 * ACTIVE pass is exactly the hole this closes. This service knows the edit
 * happened; gatepass-service owns the pass state machine, so it is told over
 * HTTP. Neither reads the other's database.
 */
@Service
public class StudentProfileService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileService.class);

    private final StudentProfileRepository studentRepository;
    private final ProfileGuard guard;
    private final PassPauseClient passPauseClient;
    private final CurrentUser currentUser;

    public StudentProfileService(StudentProfileRepository studentRepository,
                                 ProfileGuard guard,
                                 PassPauseClient passPauseClient,
                                 CurrentUser currentUser) {
        this.studentRepository = studentRepository;
        this.guard = guard;
        this.passPauseClient = passPauseClient;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ create

    /**
     * Create a student's identity profile for an account that already exists in
     * auth-service.
     *
     * One profile per account, enforced here as well as by the unique index, so
     * the caller gets "this account already has a profile" instead of a 409 from
     * a constraint name they cannot read.
     */
    @Transactional
    public StudentProfileResponse create(StudentProfileCreateDto dto) {
        currentUser.requireSameCampus(dto.getCampusId());

        if (studentRepository.existsByUserId(dto.getUserId())) {
            throw new IllegalStateException(
                    "That account already has a student profile.");
        }

        String rollNo = trimToNull(dto.getRollNo());
        requireRollNoAvailable(dto.getCampusId(), rollNo, null);
        guard.requireSelectableDepartment(dto.getCampusId(), dto.getDepartmentId());

        StudentProfile profile = StudentProfile.builder()
                .userId(dto.getUserId())
                .campusId(dto.getCampusId())
                .departmentId(dto.getDepartmentId())
                .rollNo(rollNo)
                .govId(trimToNull(dto.getGovId()))
                .address(trimToNull(dto.getAddress()))
                .photoS3Key(trimToNull(dto.getPhotoS3Key()))
                .build();

        StudentProfile saved = studentRepository.save(profile);
        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // -------------------------------------------------------------- read

    /** One profile by its own id. A student may read only their own. */
    @Transactional(readOnly = true)
    public StudentProfileResponse getOne(Long id) {
        StudentProfile profile = requireVisible(id);
        currentUser.requireSelfOrStaff(profile.getUserId());
        return StudentProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    /**
     * The lookup the React shell actually uses: the signed-in user has an
     * account id from their token, not a profile id.
     */
    @Transactional(readOnly = true)
    public StudentProfileResponse getByUserId(Long userId) {
        currentUser.requireSelfOrStaff(userId);

        StudentProfile profile = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No student profile exists for account " + userId));

        requireVisible(profile);
        return StudentProfileResponse.from(profile, guard.departmentName(profile.getDepartmentId()));
    }

    /**
     * The Student Directory. Paged, campus-scoped, optionally filtered by
     * department.
     *
     * Returns PageResponse rather than Spring's Page: Page serialises to a large
     * unstable JSON shape and logs a warning about depending on it, and the
     * frontend would be parsing an internal Spring type.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentProfileResponse> list(Long campusId, Long departmentId, Pageable pageable) {
        var page = departmentId == null
                ? studentRepository.findByCampusIdOrderByIdDesc(campusId, pageable)
                : studentRepository.findByCampusIdAndDepartmentIdOrderByIdDesc(
                        campusId, departmentId, pageable);

        // departmentName is left null here on purpose. Filling it would be one
        // extra query per row - twenty rows, twenty queries - and the directory
        // screen already has the department list loaded for its filter dropdown.
        return PageResponse.from(page, StudentProfileResponse::from);
    }

    @Transactional(readOnly = true)
    public long countByCampus(Long campusId) {
        return studentRepository.countByCampusId(campusId);
    }

    // ------------------------------------------------------------ update

    /**
     * Edit a profile.
     *
     * userId and campusId are not editable - the update DTO does not carry
     * them. Reassigning a profile to a different account or campus would orphan
     * every pass already issued against it.
     */
    @Transactional
    public StudentProfileResponse update(Long id, StudentProfileUpdateDto dto) {
        StudentProfile profile = requireVisible(id);
        currentUser.requireSelfOrStaff(profile.getUserId());

        List<String> sensitiveChanges = new ArrayList<>();

        if (dto.getRollNo() != null) {
            String rollNo = trimToNull(dto.getRollNo());
            if (!Objects.equals(rollNo, profile.getRollNo())) {
                requireRollNoAvailable(profile.getCampusId(), rollNo, profile.getId());
                profile.setRollNo(rollNo);
                sensitiveChanges.add("roll number");
            }
        }

        if (dto.getGovId() != null) {
            String govId = trimToNull(dto.getGovId());
            if (!Objects.equals(govId, profile.getGovId())) {
                profile.setGovId(govId);
                sensitiveChanges.add("government ID");
            }
        }

        if (dto.getPhotoS3Key() != null) {
            String photo = trimToNull(dto.getPhotoS3Key());
            if (!Objects.equals(photo, profile.getPhotoS3Key())) {
                profile.setPhotoS3Key(photo);
                sensitiveChanges.add("photo");
            }
        }

        // Not sensitive: a department move or a new address does not change who
        // the person at the gate is, so neither holds the pass.
        if (dto.getDepartmentId() != null
                && !dto.getDepartmentId().equals(profile.getDepartmentId())) {
            guard.requireSelectableDepartment(profile.getCampusId(), dto.getDepartmentId());
            profile.setDepartmentId(dto.getDepartmentId());
        }

        if (dto.getAddress() != null) {
            profile.setAddress(trimToNull(dto.getAddress()));
        }

        StudentProfile saved = studentRepository.save(profile);

        // After the save, deliberately. If gatepass-service is unreachable the
        // edit still stands and the failure is logged - see PassPauseClient.
        if (!sensitiveChanges.isEmpty()) {
            pauseHolder(saved.getUserId(), sensitiveChanges);
        }

        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // ----------------------------------------------------------- helpers

    private void pauseHolder(Long userId, List<String> changes) {
        String reason = "Profile change requires re-approval: " + String.join(", ", changes);
        boolean paused = passPauseClient.pauseAllForHolder(userId, reason, currentUser.userId());
        if (!paused) {
            log.warn("Sensitive change on student account {} ({}) but no pass was paused.",
                    userId, reason);
        }
    }

    /**
     * A roll number is unique per campus, never globally - two campuses may run
     * the same numbering scheme and neither is wrong.
     *
     * selfId lets an update skip the row being edited, so saving a profile
     * without touching its roll number does not collide with itself.
     */
    private void requireRollNoAvailable(Long campusId, String rollNo, Long selfId) {
        if (rollNo == null) {
            return;
        }
        Optional<StudentProfile> holder =
                studentRepository.findByCampusIdAndRollNoIgnoreCase(campusId, rollNo);

        if (holder.isPresent() && !holder.get().getId().equals(selfId)) {
            throw new IllegalStateException(
                    "Roll number \"" + rollNo + "\" is already used on this campus.");
        }
    }

    /**
     * Load or 404.
     *
     * A profile on another campus reads as "not found" rather than "forbidden".
     * A 403 would confirm the row exists, which is enough to enumerate how many
     * students another campus has by walking the ids.
     */
    private StudentProfile requireVisible(Long id) {
        StudentProfile profile = studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student profile", id));
        return requireVisible(profile);
    }

    private StudentProfile requireVisible(StudentProfile profile) {
        if (!currentUser.canSeeCampus(profile.getCampusId())) {
            throw ResourceNotFoundException.of("Student profile", profile.getId());
        }
        return profile;
    }

    /** "" and "   " both mean "no value", so they are stored as NULL, not as blanks. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
