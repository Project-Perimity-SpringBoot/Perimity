package com.perimity.user.service;

import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the empty profile that belongs to a newly created account.
 *
 * ==========================================================================
 * WHY THIS EXISTS
 * ==========================================================================
 * An account lives in auth-service and a STUDENT's or FACULTY's profile lives
 * here. Until now the only thing that ever created the second record was the
 * React Add Student screen, making a second API call after the first returned.
 *
 * That is a dual write performed by a browser. If the second call did not
 * happen - a closed tab, a dropped connection, an account created from any
 * other screen - the account existed and the profile did not. The person could
 * sign in and then found every profile page telling them they did not exist,
 * and nothing in the product could create the missing row afterwards. Several
 * accounts on this system are still like that, including every faculty account,
 * which is why no visitor can pick a host.
 *
 * ==========================================================================
 * DELIBERATELY NOT IN CurrentUser's WORLD
 * ==========================================================================
 * This runs from a queue listener. There is no request, no JWT and no
 * SecurityContext, so nothing here may call CurrentUser - it would throw
 * "No authenticated user on this request" on every message. Every value comes
 * from the event, which came from auth-service, which is the only thing that
 * knows a new account exists.
 *
 * ProfileLookupService carries the same warning for the same reason.
 *
 * ==========================================================================
 * IDEMPOTENT, BECAUSE REDELIVERY IS NORMAL
 * ==========================================================================
 * A broker redelivers whenever a consumer dies between doing the work and
 * acknowledging it. So provisioning twice must be harmless: the existence check
 * handles the common case, and the unique index on user_id handles the race
 * where two deliveries are processed at once. Catching the constraint violation
 * and treating it as success is what makes the second delivery a no-op rather
 * than a poison message that dead-letters forever.
 */
@Service
public class ProfileProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProfileProvisioningService.class);

    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;

    public ProfileProvisioningService(StudentProfileRepository studentRepository,
                                      FacultyProfileRepository facultyRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
    }

    /**
     * @return true when a row was created, false when one already existed or the
     *         role has no profile. Only used for logging - both are success.
     */
    @Transactional
    public boolean provision(Long userId, String role, Long campusId) {
        if (userId == null || role == null) {
            // Nothing to act on, and retrying will not improve it. Logged and
            // dropped rather than dead-lettered - a message this malformed is a
            // producer bug, and the DLQ should hold things worth retrying.
            log.error("user.created with no userId or role - ignoring: userId={} role={}",
                    userId, role);
            return false;
        }

        /*
         * campusId is NOT NULL on both entities. A STUDENT or FACULTY without
         * one is an auth-service bug: only SUPER_ADMIN is allowed a null campus
         * and SUPER_ADMIN has no profile.
         *
         * Thrown rather than logged, so the message retries and then lands in
         * the DLQ where somebody can see it. Inserting is not an option - the
         * constraint would reject it - and swallowing would recreate exactly the
         * silent orphan this class was written to abolish.
         */
        if (campusId == null) {
            throw new IllegalStateException(
                    "user.created for " + role + " account " + userId
                            + " carried no campusId. A profile cannot be created without one.");
        }

        return switch (role) {
            case "STUDENT" -> provisionStudent(userId, campusId);
            case "FACULTY" -> provisionFaculty(userId, campusId);
            /*
             * GUARD, CAMPUS_ADMIN, SUPER_ADMIN and VISITOR have no profile
             * entity. Not an error - auth-service filters these out before
             * publishing, and this branch only catches a role added there later
             * that nobody taught this service about. Better a log line than an
             * exception that dead-letters a message which was never actionable.
             */
            default -> {
                log.debug("No profile type for role {} - account {} needs none.", role, userId);
                yield false;
            }
        };
    }

    private boolean provisionStudent(Long userId, Long campusId) {
        if (studentRepository.existsByUserId(userId)) {
            log.debug("Student profile already exists for account {} - redelivery.", userId);
            return false;
        }
        try {
            /*
             * userId and campusId only. rollNo, departmentId and govId are staff
             * decisions made later on the Add Student or profile screens, and
             * inventing placeholders would put fake data in a field a guard
             * reads. verificationStatus defaults to DRAFT via @Builder.Default,
             * which is exactly right: nobody has checked anything yet.
             */
            StudentProfile saved = studentRepository.save(StudentProfile.builder()
                    .userId(userId)
                    .campusId(campusId)
                    .build());
            log.info("Provisioned student profile {} for account {} on campus {}.",
                    saved.getId(), userId, campusId);
            return true;

        } catch (DataIntegrityViolationException ex) {
            // The unique index on user_id fired: a concurrent delivery won.
            // That is the outcome we wanted, so this is success.
            log.debug("Student profile for account {} was created concurrently.", userId);
            return false;
        }
    }

    private boolean provisionFaculty(Long userId, Long campusId) {
        if (facultyRepository.existsByUserId(userId)) {
            log.debug("Faculty profile already exists for account {} - redelivery.", userId);
            return false;
        }
        try {
            FacultyProfile saved = facultyRepository.save(FacultyProfile.builder()
                    .userId(userId)
                    .campusId(campusId)
                    .build());
            log.info("Provisioned faculty profile {} for account {} on campus {}. "
                            + "They can now be picked as a visitor's host.",
                    saved.getId(), userId, campusId);
            return true;

        } catch (DataIntegrityViolationException ex) {
            log.debug("Faculty profile for account {} was created concurrently.", userId);
            return false;
        }
    }
}
