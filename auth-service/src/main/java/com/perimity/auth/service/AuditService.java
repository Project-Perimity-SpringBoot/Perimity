package com.perimity.auth.service;

import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The append-only security trail (FR-AUD-1).
 *
 * Two rules, both load-bearing:
 *
 * 1. NEVER put a password, an OTP or a token in `details`, not even hashed.
 *    The audit table is the one most likely to be exported, emailed and pasted
 *    into a support ticket.
 *
 * 2. Writing an audit row must NEVER fail the action it describes. A failed
 *    insert here would roll back a successful login, which is absurd.
 *
 * NOTHING IN THIS CLASS IS @Transactional, and that is the fix rather than an
 * oversight. The transaction lives in AuditWriter, one bean along, so that the
 * try/catch here sits OUTSIDE the transaction boundary and can actually catch a
 * commit failure. The previous version put @Transactional and the try/catch on
 * the same method, which cannot work: the commit runs in the proxy after the
 * method returns, so a rollback-only transaction still threw
 * UnexpectedRollbackException straight past the catch and into the caller.
 *
 * That bug was invisible for as long as every insert succeeded. It surfaced the
 * first time one failed - a new enum value hitting an old CHECK constraint -
 * and took the whole request down, which is precisely the outcome rule 2
 * exists to prevent.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditWriter writer;

    public AuditService(AuditWriter writer) {
        this.writer = writer;
    }

    /** An event this service observed itself. Source IP comes from the request. */
    public void record(AuditAction action, Long actorUserId, Role actorRole,
                       Long campusId, String targetEntity, String details) {
        safely(action, actorUserId, actorRole, campusId, targetEntity, currentIp(), details);
    }

    /** For anonymous events - a failed login by an address with no account. */
    public void recordAnonymous(AuditAction action, String targetEntity, String details) {
        record(action, null, null, null, targetEntity, details);
    }

    /**
     * An event that happened in ANOTHER service (Day 11).
     *
     * sourceIp is passed in rather than read from the request. currentIp() would
     * return the calling service's own container address - identical on every
     * row, for every guard, at every gate - which answers no question anyone
     * would ask the audit log.
     */
    public void recordFromService(AuditAction action, Long actorUserId, Role actorRole,
                                  Long campusId, String targetEntity, String details,
                                  String sourceIp) {
        // Falls back to the caller's address only when nothing was forwarded:
        // better than null, and visibly wrong in a way that prompts a fix.
        String ip = (sourceIp == null || sourceIp.isBlank()) ? currentIp() : sourceIp;
        safely(action, actorUserId, actorRole, campusId, targetEntity, ip, details);
    }

    /**
     * The single point where an audit failure is absorbed.
     *
     * Catches Exception, not RuntimeException: a commit failure can surface as
     * either, and the whole point is that nothing gets out of here.
     */
    private void safely(AuditAction action, Long actorUserId, Role actorRole, Long campusId,
                        String targetEntity, String sourceIp, String details) {
        try {
            writer.write(action, actorUserId, actorRole, campusId,
                    targetEntity, sourceIp, sanitise(details));
        } catch (Exception ex) {
            // Deliberately swallowed - see the class comment. Logged at ERROR
            // because a silently missing audit row is a security problem, even
            // though it must not be a user-facing one.
            log.error("Could not write audit row for {}: {}", action, ex.getMessage());
        }
    }

    /**
     * Strips newlines. An audit line is read in a terminal and in exports, and
     * a value containing a newline can fake a second entry - the same log
     * forging problem Palash found in guard-service.
     */
    private String sanitise(String details) {
        if (details == null) {
            return null;
        }
        String flat = details.replaceAll("[\\r\\n\\t]", " ").trim();
        return flat.length() > 500 ? flat.substring(0, 497) + "..." : flat;
    }

    /**
     * Best effort. X-Forwarded-For is checked first because behind CloudFront
     * the remote address is the load balancer, not the caller - which would
     * make every row identical and useless.
     */
    private String currentIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
