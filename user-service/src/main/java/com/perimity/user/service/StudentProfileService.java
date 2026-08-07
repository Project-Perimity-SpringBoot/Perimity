package com.perimity.user.service;

import com.perimity.user.client.PassPauseClient;
import com.perimity.user.dto.request.StudentProfileCreateDto;
import com.perimity.user.dto.request.StudentProfileUpdateDto;
import com.perimity.user.dto.request.StudentSelfDetailsDto;
import com.perimity.user.dto.request.StudentVerificationDecisionDto;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import java.time.LocalDateTime;
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
    private final StudentPassIssuer passIssuer;
    private final CurrentUser currentUser;

    public StudentProfileService(StudentProfileRepository studentRepository,
                                 ProfileGuard guard,
                                 PassPauseClient passPauseClient,
                                 StudentPassIssuer passIssuer,
                                 CurrentUser currentUser) {
        this.studentRepository = studentRepository;
        this.guard = guard;
        this.passPauseClient = passPauseClient;
        this.passIssuer = passIssuer;
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

        String rollNo = trimToNull(dto.getRollNo());
        guard.requireSelectableDepartment(dto.getCampusId(), dto.getDepartmentId());

        /*
         * ==================================================================
         *  CREATE-OR-FILL, NOT CREATE-OR-FAIL
         * ==================================================================
         * This used to throw "That account already has a student profile" when
         * a row existed. That was right when nothing else created profiles.
         *
         * It is wrong now. A user.created event provisions an EMPTY profile the
         * moment the account is made, so by the time the Add Student screen
         * makes its second call the row usually already exists - and which of
         * the two arrives first is a race between a queue and an HTTP round
         * trip. Failing on the losing side would break the one screen that has
         * always done this correctly, intermittently, which is the worst way to
         * break anything.
         *
         * So an existing row is filled in rather than refused. The caller's
         * intent - "this student has this roll number and department" - is the
         * same either way, and the endpoint is staff-only and campus-scoped.
         *
         * The self-declared fields are NOT touched here. If a student has
         * already entered their own name or phone number, a staff member
         * setting a roll number must not wipe it, and their verification status
         * must not silently change.
         */
        /*
         * ==================================================================
         *  AND THE ROW IS MADE SURE OF FIRST, NOT LOOKED FOR
         * ==================================================================
         * "Find it, insert one if it is missing" is the obvious shape and it
         * is the one that fails. The listener is provisioning the row on
         * another thread at the same moment this request arrives - the browser
         * calls this endpoint the instant the account comes back, which is the
         * instant auth-service published the event - so a profile that is
         * genuinely absent at the SELECT can exist by the INSERT. That is
         * uk_student_user, surfaced to faculty as "The request conflicts with
         * existing data" on a screen where they did nothing wrong, leaving an
         * account with no profile behind it.
         *
         * Catching the violation here does not work either: Hibernate marks
         * the transaction rollback-only when a constraint fails, so the
         * recovery save would fail again at commit. insertIfAbsent pushes the
         * race into the database, where ON CONFLICT DO NOTHING settles it
         * without an exception. Whatever happens, a row exists after this line
         * and the rest of the method is a fill-in.
         */
        studentRepository.insertIfAbsent(dto.getUserId(), dto.getCampusId());

        StudentProfile profile = studentRepository.findByUserId(dto.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "Profile for account " + dto.getUserId()
                                + " could not be created or read."));

        requireVisible(profile);
        requireRollNoAvailable(dto.getCampusId(), rollNo, profile.getId());

        if (rollNo != null) {
            profile.setRollNo(rollNo);
        }
        if (dto.getDepartmentId() != null) {
            profile.setDepartmentId(dto.getDepartmentId());
        }
        if (trimToNull(dto.getGovId()) != null) {
            profile.setGovId(trimToNull(dto.getGovId()));
        }
        if (trimToNull(dto.getAddress()) != null) {
            profile.setAddress(trimToNull(dto.getAddress()));
        }
        if (trimToNull(dto.getPhotoS3Key()) != null) {
            profile.setPhotoS3Key(trimToNull(dto.getPhotoS3Key()));
        }

        StudentProfile saved = studentRepository.save(profile);
        log.info("Filled in student profile {} for account {}.",
                saved.getId(), saved.getUserId());

        /*
         * ==================================================================
         *  THE PASS, WHICH NOTHING ON THIS PATH USED TO ISSUE
         * ==================================================================
         * A student added one at a time got an account and a profile and no
         * pass - ever. The only code in the product that issued a student pass
         * was the bulk import, so the two ways of adding the same person
         * produced different people: one who could get through the gate and one
         * who could not.
         *
         * Last, after the profile is saved, and it cannot fail this method -
         * see StudentPassIssuer, which swallows everything. gatepass being down
         * must not turn a created student into a 500 the browser reports as
         * "the account was not created", when it was.
         */
        passIssuer.ensureStandingPass(saved.getUserId(), saved.getCampusId(), null);

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
        //
        // forDirectory, NOT from. The directory answers "who is on this campus",
        // which needs no address, date of birth or phone number. Returning them
        // would let any faculty account page through the campus and collect every
        // student's contact details through a screen that looks entirely routine.
        // The single-profile read and the verification queue still get the full
        // record, because those are the places somebody genuinely needs it.
        return PageResponse.from(page, StudentProfileResponse::forDirectory);
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

        if (!sensitiveChanges.isEmpty()) {
            pauseHolder(saved.getUserId(), sensitiveChanges);
            saved.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);
            saved.setSubmittedAt(LocalDateTime.now());
            saved = studentRepository.save(saved);
        }

        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // ============================================================
    //  SELF-DECLARED DETAILS AND VERIFICATION
    //
    //  The state machine lives here and nowhere else. A controller that
    //  set verificationStatus directly, or a repository save that skipped
    //  these methods, would let a profile reach VERIFIED without anyone
    //  having looked at it - which is the only thing the status is for.
    // ============================================================

    /**
     * PUT /api/user/students/me/details - the student writing their own facts.
     *
     * Whole-object replacement, NOT the null-means-leave-it-alone contract used
     * by update() above. Two different shapes on purpose: update() serves partial
     * admin edits from several screens, while this serves one form the student
     * fills in completely and submits. Applying the patch semantics to a form
     * that always posts every field would silently keep stale values in any box
     * the student cleared.
     *
     * ---------------------------------------------------------------
     * WHY EDITING A VERIFIED PROFILE SENDS IT BACK TO DRAFT
     * ---------------------------------------------------------------
     * If a verified profile could be edited in place, the record would go on
     * claiming a named member of staff checked details they never saw. That is
     * worse than no verification at all - it is a false attestation with
     * somebody's user id attached to it.
     *
     * So an edit clears the whole decision: status back to DRAFT, verifiedBy,
     * verifiedAt and remarks all wiped. The student resubmits and someone looks
     * again. Cheap to do, and the alternative is a lie in the database.
     */
    @Transactional
    public StudentProfileResponse updateOwnDetails(Long userId, StudentSelfDetailsDto dto) {
        currentUser.requireSelfOrStaff(userId);

        StudentProfile profile = findOrCreateProfileOf(userId);
        requireEditable(profile);

        /*
         * ==================================================================
         *  READ BEFORE WRITE: was this record one somebody had already checked?
         * ==================================================================
         * Captured here because the setters below destroy the answer, and the
         * answer is what decides whether the pass gets held. See the pause
         * block further down.
         */
        boolean wasVerified = status(profile) == ProfileVerificationStatus.VERIFIED;

        profile.setFirstName(trimToNull(dto.getFirstName()));
        profile.setMiddleName(trimToNull(dto.getMiddleName()));
        profile.setLastName(trimToNull(dto.getLastName()));
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setGender(dto.getGender());
        profile.setAddress(trimToNull(dto.getAddress()));
        profile.setPhoneCountryCode(trimToNull(dto.getPhoneCountryCode()));
        profile.setPhoneNumber(trimToNull(dto.getPhoneNumber()));
        profile.setAltPhoneCountryCode(trimToNull(dto.getAltPhoneCountryCode()));
        profile.setAltPhoneNumber(trimToNull(dto.getAltPhoneNumber()));

        /*
         * ==================================================================
         *  PAUSE ONLY WHEN THERE WAS A VERIFICATION TO INVALIDATE
         * ==================================================================
         * This used to be an UNCONDITIONAL pauseHolder on every save, and it is
         * the reason students ended up with permanently dead passes. Correcting
         * a typo in an address held the pass; so did saving a half-finished form
         * twice. Combined with the fact that nothing could resume a pass, every
         * save was a one-way trip.
         *
         * The rule that actually matters: a pass rests on a CHECKED profile. If
         * this record was VERIFIED, the student has just changed the details a
         * named member of staff signed off, so the sign-off is void and the pass
         * must be held until somebody looks again - which is exactly what the
         * status change below does.
         *
         * If it was DRAFT or REJECTED there is no verification to invalidate.
         * Nothing about the pass changes, so nothing is held.
         *
         * NONE OF THESE FIELDS REACH THE GATE, which is worth saying because it
         * looks like they should. StudentProfile is explicit that auth-service's
         * User.name is the authoritative name and the one a pass carries; these
         * three name parts are supporting detail. The fields a guard actually
         * sees - photo and roll number - are not on this form and are handled by
         * update(), which pauses on a real change to any of them.
         *
         * The remarks note that used to live here: a REJECTED profile keeps its
         * remarks on purpose, because the student is editing precisely because
         * they were told what was wrong. Those are cleared on the next submit.
         */
        if (wasVerified) {
            pauseHolder(userId, List.of("verified details were edited"));
        }

        if (isCompleteForSubmission(profile)) {
            profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);
            profile.setSubmittedAt(LocalDateTime.now());
        } else {
            profile.setVerificationStatus(ProfileVerificationStatus.DRAFT);
        }

        StudentProfile saved = studentRepository.save(profile);
        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    /**
     * POST /api/user/students/me/details/submit - "I have finished, please check".
     *
     * Separate from the save above because saving and submitting are different
     * intentions. A student part-way through the form must be able to keep their
     * work without it landing in a reviewer's queue half-finished.
     *
     * Guarded against double submission: calling this twice would move
     * submittedAt forward and push the student to the back of a queue ordered by
     * that column, so an impatient student clicking twice would be punished for
     * it.
     */
    @Transactional
    public StudentProfileResponse submitOwnDetails(Long userId) {
        currentUser.requireSelfOrStaff(userId);

        StudentProfile profile = requireProfileOf(userId);
        ProfileVerificationStatus current = status(profile);

        if (current == ProfileVerificationStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Your details are already with faculty for checking.");
        }
        if (!current.canSubmit()) {
            throw new IllegalStateException(
                    "Your details are already verified. Edit them if something has changed.");
        }

        requireCompleteForSubmission(profile);

        // Remarks from a previous rejection are cleared here, not on the next
        // decision. Leaving them would show the student a stale reason next to
        // details they have already corrected.
        profile.setVerificationRemarks(null);
        profile.setVerificationStatus(ProfileVerificationStatus.SUBMITTED);
        profile.setSubmittedAt(LocalDateTime.now());

        StudentProfile saved = studentRepository.save(profile);
        log.info("Student account {} submitted profile {} for verification.", userId, saved.getId());
        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    /** GET /api/user/students/pending - the reviewer's queue, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<StudentProfileResponse> listPendingVerification(Long campusId,
                                                                       Pageable pageable) {
        currentUser.requireProfileReviewer();
        var page = studentRepository.findByCampusIdAndVerificationStatusOrderBySubmittedAtAsc(
                campusId, ProfileVerificationStatus.SUBMITTED, pageable);
        return PageResponse.from(page, StudentProfileResponse::from);
    }

    @Transactional(readOnly = true)
    public long countPendingVerification(Long campusId) {
        currentUser.requireProfileReviewer();
        return studentRepository.countByCampusIdAndVerificationStatus(
                campusId, ProfileVerificationStatus.SUBMITTED);
    }

    /**
     * PATCH /api/user/students/{id}/verification - the reviewer's decision.
     *
     * The identity of the reviewer is taken from the security context, never
     * from the request body. StudentVerificationDecisionDto has no verifiedBy
     * field precisely so that this cannot be got wrong later.
     *
     * Only a SUBMITTED profile can be decided. Refusing to act on a DRAFT is
     * what stops a reviewer approving details a student is still editing, and
     * refusing to act on an already-VERIFIED one stops two reviewers racing and
     * the second silently overwriting the first one's record.
     */
    @Transactional
    public StudentProfileResponse decideVerification(Long profileId,
                                                     StudentVerificationDecisionDto dto) {
        currentUser.requireProfileReviewer();

        StudentProfile profile = requireVisible(profileId);

        if (!status(profile).isAwaitingDecision()) {
            throw new IllegalStateException(
                    "These details are not waiting for a decision - they are "
                            + status(profile) + ".");
        }

        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        Long reviewerId = currentUser.userId();

        profile.setVerificationStatus(approved
                ? ProfileVerificationStatus.VERIFIED
                : ProfileVerificationStatus.REJECTED);
        profile.setVerifiedBy(reviewerId);
        profile.setVerifiedAt(LocalDateTime.now());
        profile.setVerificationRemarks(trimToNull(dto.getRemarks()));

        StudentProfile saved = studentRepository.save(profile);

        /*
         * ==============================================================
         *  APPROVAL IS WHAT RELEASES THE PASS
         * ==============================================================
         * This is the other end of the pause rule, and until now it did not
         * exist. A sensitive edit held every pass the student had; nothing
         * anywhere released one. The student's pass page said "staff re-verify
         * and it resumes", staff re-verified, and it did not.
         *
         * Only on APPROVAL. A rejection means the details are still wrong, so
         * the pass must stay held - resuming there would put a working pass in
         * the pocket of somebody a reviewer has just refused.
         *
         * Never throws: the decision above is already committed and is correct
         * on its own. A gatepass outage must not turn a recorded approval into
         * a 500 that makes the reviewer approve again.
         */
        if (approved) {
            boolean resumed = passPauseClient.resumeAllForHolder(
                    saved.getUserId(),
                    "Profile re-approved by staff",
                    reviewerId);

            if (!resumed) {
                log.warn("Profile {} was verified but its holder's passes could not be resumed. "
                        + "Resume them from the student list.", saved.getId());
            }
        }

        // Ids only. A rejection reason is free text written by one user and read
        // by another, and free text in a log line is how log forgery starts.
        log.info("Profile {} {} by account {}.",
                saved.getId(), approved ? "verified" : "rejected", reviewerId);

        return StudentProfileResponse.from(saved, guard.departmentName(saved.getDepartmentId()));
    }

    // ----------------------------------------------------------- helpers

    /**
     * Rows written before these columns existed have a null status, because
     * ddl-auto=update adds a column without backfilling it. Never verified is
     * exactly what those rows are, so they read as DRAFT.
     */
    private static ProfileVerificationStatus status(StudentProfile profile) {
        return profile.getVerificationStatus() == null
                ? ProfileVerificationStatus.DRAFT
                : profile.getVerificationStatus();
    }

    /**
     * SUBMITTED is the one state a student may not write in - faculty are
     * reading the details, and letting them change underneath is how a reviewer
     * ends up approving something they never saw.
     *
     * VERIFIED is editable, but as an explicit act that clears the decision.
     * See updateOwnDetails.
     */
    private void requireEditable(StudentProfile profile) {
        if (status(profile) == ProfileVerificationStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Your details are with faculty for checking and cannot be edited. "
                            + "Wait for the outcome.");
        }
    }

    /**
     * Everything mandatory must be present before a reviewer is asked to look.
     *
     * The DTO already enforces this on the way in, so this only catches profiles
     * created by staff that the student has never opened - the common case, since
     * faculty create the account and the student fills in the rest. Without this
     * check those would submit as blank rows and waste a reviewer's time.
     */
    private void requireCompleteForSubmission(StudentProfile profile) {
        List<String> missing = new ArrayList<>();

        if (isBlank(profile.getFirstName())) {
            missing.add("first name");
        }
        if (isBlank(profile.getLastName())) {
            missing.add("last name");
        }
        if (profile.getDateOfBirth() == null) {
            missing.add("date of birth");
        }
        if (profile.getGender() == null) {
            missing.add("gender");
        }
        if (isBlank(profile.getAddress())) {
            missing.add("address");
        }
        if (isBlank(profile.getPhoneNumber()) || isBlank(profile.getPhoneCountryCode())) {
            missing.add("phone number");
        }

        /*
         * THE PHOTO IS MANDATORY, and it is the one item on this list that is
         * not merely administrative.
         *
         * Everything else here is a fact a reviewer reads. The photo is the
         * thing a guard holds against the face in front of them at the gate,
         * and it is the only field on the profile that does the access-control
         * job at all. A verified profile with no photo says somebody checked a
         * person they had no way to recognise.
         *
         * Named separately in the message because "upload a photo" is a
         * different action from "fill in a box", and a student who reads
         * "photo" in a list of text fields will go looking for a text field.
         */
        if (isBlank(profile.getPhotoS3Key())) {
            missing.add("passport photo");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Fill these in before submitting: " + String.join(", ", missing) + ".");
        }
    }

    public static boolean isCompleteForSubmission(StudentProfile profile) {
        return profile != null
                && !isBlank(profile.getFirstName())
                && !isBlank(profile.getLastName())
                && profile.getDateOfBirth() != null
                && profile.getGender() != null
                && !isBlank(profile.getAddress())
                && !isBlank(profile.getPhoneNumber())
                && !isBlank(profile.getPhotoS3Key());
    }

    /** Wipes a previous decision so a re-review starts from nothing. */
    private static void clearDecision(StudentProfile profile) {
        profile.setVerifiedBy(null);
        profile.setVerifiedAt(null);
        profile.setVerificationRemarks(null);
        profile.setSubmittedAt(null);
    }

    private StudentProfile requireProfileOf(Long userId) {
        StudentProfile profile = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No student profile exists for account " + userId));
        return requireVisible(profile);
    }

    /**
     * The student's profile, created empty if it does not exist yet.
     *
     * ==================================================================
     *  WHY A PUT CREATES THE ROW
     * ==================================================================
     * An account in auth-service and a profile in user-service are two separate
     * records, and until now exactly ONE thing in the whole product created the
     * second one: the faculty Add Student screen, client-side, in a step after
     * the account. Any account that arrived another way - or whose second step
     * failed - could sign in and then found every profile screen answering
     * "No student profile exists for account 21".
     *
     * The student could not fix that themselves, and no screen anywhere let
     * anyone else fix it either. The account was simply stuck.
     *
     * PUT is create-or-replace by definition, so a PUT to "my details" creating
     * the row is what the verb already means rather than a special case. It also
     * makes the whole class of orphaned accounts self-healing: the student opens
     * their own form, fills it in, and the row appears.
     *
     * SAFE, because nothing here is caller-supplied. userId and campusId both
     * come from the verified token, so this cannot mint a profile for somebody
     * else or attach one to another campus. rollNo, departmentId and govId are
     * deliberately left null - those are staff decisions, and a student
     * inventing their own roll number is exactly what the create DTO refuses.
     *
     * The unique index on user_id is the backstop: two racing requests cannot
     * produce two profiles, one gets a constraint violation.
     */
    private StudentProfile findOrCreateProfileOf(Long userId) {
        return studentRepository.findByUserId(userId)
                .map(this::requireVisible)
                .orElseGet(() -> {
                    Long campusId = currentUser.campusId();
                    StudentProfile created = studentRepository.save(StudentProfile.builder()
                            .userId(userId)
                            .campusId(campusId)
                            .build());
                    log.info("Created student profile {} on first use for account {} (campus {}).",
                            created.getId(), userId, campusId);
                    return created;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

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
