package com.perimity.user.service;

import com.perimity.user.client.AuthFeignClient;
import com.perimity.user.client.GatepassFeignClient;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues a student's standing DAILY pass.
 *
 * ==========================================================================
 * WHY THIS EXISTS AS ITS OWN THING
 * ==========================================================================
 * Until now exactly one place in the product issued a student pass: the bulk
 * import, inline in its confirm loop. A student added through the Add Student
 * screen therefore never got one - not at creation, not when faculty verified
 * their profile, not ever. The account worked, the profile filled in, and the
 * gate stayed shut with nothing anywhere explaining why.
 *
 * Both routes now call this. One implementation means the two cannot drift into
 * issuing subtly different passes, which is what "copy the six lines across"
 * would have guaranteed within a month.
 *
 * ==========================================================================
 * IT NEVER THROWS, AND THAT IS THE WHOLE CONTRACT
 * ==========================================================================
 * The callers are mid-transaction writing a profile. gatepass-service being
 * slow, down, or refusing must not undo an account and a profile that are
 * otherwise correct - a student with a profile and no pass is recoverable, a
 * student with neither is a row somebody has to reconstruct by hand.
 *
 * So every failure is logged and swallowed, and the caller carries on. The pass
 * is safe to ask for again later: gatepass returns the existing standing pass
 * rather than minting a second, so a retry is free.
 *
 * ==========================================================================
 * THE PASS IS ISSUED BEFORE THE PHOTO EXISTS
 * ==========================================================================
 * Stated plainly because it is a real trade. A pass whose profile has no photo
 * gives a guard nothing to check a face against, and the import's own notes
 * argue for waiting until one is uploaded.
 *
 * Issuing at creation is the behaviour that was asked for: students get their
 * pass the moment their account is made rather than waiting on a verification
 * queue. The pass is created PENDING and only goes ACTIVE once qr-service has
 * generated its QR, and any later change to the photo or roll number pauses it
 * through the existing SRS v1.1 rule - so the window this opens is bounded by
 * the same machinery that was already there.
 */
@Component
public class StudentPassIssuer {

    private static final Logger log = LoggerFactory.getLogger(StudentPassIssuer.class);

    private final GatepassFeignClient gatepassClient;
    private final AuthFeignClient authClient;

    public StudentPassIssuer(GatepassFeignClient gatepassClient, AuthFeignClient authClient) {
        this.gatepassClient = gatepassClient;
        this.authClient = authClient;
    }

    /**
     * Ensure this student holds a standing DAILY pass.
     *
     * @param holderName the name for the pass, or null to look it up. The bulk
     *                   import already has it from the sheet; the Add Student
     *                   path does not and pays for one call.
     */
    public void ensureStandingPass(Long userId, Long campusId, String holderName) {
        if (userId == null || campusId == null) {
            log.warn("Cannot issue a standing pass without both a user id and a campus "
                    + "(userId={}, campusId={}).", userId, campusId);
            return;
        }

        String name = holderName == null || holderName.isBlank() ? lookUpName(userId) : holderName;

        if (name == null || name.isBlank()) {
            /*
             * holderName is @NotNull on the other side and is trimmed there, so
             * sending a blank would be a 400 dressed up as a mystery. Refused
             * here instead, with the reason.
             */
            log.warn("No account name for user {} - the standing pass was not issued. "
                    + "Issue it by hand once the name is known.", userId);
            return;
        }

        try {
            gatepassClient.issuePass(new GatepassFeignClient.IssuePassRequest(
                    userId,
                    name,
                    campusId,
                    null,
                    "DAILY",
                    null,
                    // Valid from today, with NO end date. That is what makes it
                    // standing - see GatePassCreateDto, where a null validTo is
                    // legal for DAILY and mandatory-absent for EVENT.
                    LocalDate.now(),
                    null));

            log.info("Standing DAILY pass ensured for student {} (user {}) on campus {}.",
                    name, userId, campusId);

        } catch (RuntimeException ex) {
            /*
             * The root cause, not ex.getMessage(). Feign's circuit breaker wraps
             * everything as NoFallbackAvailableException("No fallback
             * available"), which says nothing about whether gatepass was
             * unreachable, refused the body, or timed out.
             */
            log.error("Could not issue the standing pass for user {} on campus {}: {}",
                    userId, campusId, rootCause(ex));
        }
    }

    /** Never throws - a name that cannot be read is reported by the caller. */
    private String lookUpName(Long userId) {
        try {
            AuthFeignClient.UserEnvelope envelope = authClient.findById(userId);
            return envelope == null || envelope.data() == null ? null : envelope.data().name();
        } catch (RuntimeException ex) {
            log.warn("Could not read the account name for user {}: {}", userId, rootCause(ex));
            return null;
        }
    }

    private static String rootCause(Throwable ex) {
        Throwable current = ex;
        for (int i = 0; i < 10 && current.getCause() != null; i++) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
