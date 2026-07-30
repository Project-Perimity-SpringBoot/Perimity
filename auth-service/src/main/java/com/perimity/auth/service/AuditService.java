package com.perimity.auth.service;

import com.perimity.auth.entity.AuditLog;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 *    insert here would roll back a successful login, which is absurd - so every
 *    write runs in its own transaction and swallows its own errors.
 *
 * REQUIRES_NEW matters for the opposite case too: a LOGIN_FAILED row must
 * survive even though the surrounding request throws.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, Long actorUserId, Role actorRole,
                       Long campusId, String targetEntity, String details) {
        try {
            repository.save(AuditLog.builder()
                    .action(action)
                    .actorUserId(actorUserId)
                    .actorRole(actorRole)
                    .campusId(campusId)
                    .targetEntity(targetEntity)
                    .sourceIp(currentIp())
                    .details(sanitise(details))
                    .build());
        } catch (RuntimeException ex) {
            // Deliberately swallowed. See the class comment.
            log.error("Could not write audit row for {}: {}", action, ex.getMessage());
        }
    }

    /** For anonymous events - a failed login by an address with no account. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(AuditAction action, String targetEntity, String details) {
        record(action, null, null, null, targetEntity, details);
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
