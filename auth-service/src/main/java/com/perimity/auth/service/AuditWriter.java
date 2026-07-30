package com.perimity.auth.service;

import com.perimity.auth.entity.AuditLog;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one transactional insert into audit_logs. A SEPARATE BEAN on purpose.
 *
 * AuditService promises that writing an audit row never fails the action it
 * describes. It used to try to keep that promise with a try/catch inside its
 * own @Transactional method, and that DOES NOT WORK - which is worth
 * understanding, because it is a trap the whole team can fall into:
 *
 *   @Transactional(REQUIRES_NEW)
 *   public void record(...) {
 *       try   { repository.save(...); }   // throws, tx marked rollback-only
 *       catch { log.error(...); }         // swallowed - looks handled
 *   }                                     // <- proxy COMMITS here, and the
 *                                         //    commit throws
 *                                         //    UnexpectedRollbackException
 *
 * The catch runs inside the transaction. The commit happens after the method
 * returns, in the proxy, where no catch of yours can reach it. So the
 * exception escapes anyway and takes the caller down with it - exactly what
 * the try/catch was written to prevent.
 *
 * Splitting the write into its own bean puts the transaction boundary INSIDE
 * the try/catch instead of around it. AuditService calls this, catches
 * everything including the commit failure, and carries on. A self-injected
 * proxy would work too and reads far worse.
 */
@Component
class AuditWriter {

    private final AuditLogRepository repository;

    AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * REQUIRES_NEW so a LOGIN_FAILED row survives the rollback of the request
     * that produced it. A failed login that leaves no trace is the exact hole
     * this table exists to close.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(AuditAction action, Long actorUserId, Role actorRole, Long campusId,
               String targetEntity, String sourceIp, String details) {

        repository.save(AuditLog.builder()
                .action(action)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .campusId(campusId)
                .targetEntity(targetEntity)
                .sourceIp(sourceIp)
                .details(details)
                .build());
    }
}
