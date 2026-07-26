package com.perimity.auth.repository;

import com.perimity.auth.entity.AuditLog;
import com.perimity.auth.entity.enums.AuditAction;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read and insert only. Never expose an update or delete on this repository -
 * an audit trail that can be edited is not an audit trail.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Screen 18 - Audit Log, scoped to one campus. */
    Page<AuditLog> findByCampusIdOrderByCreatedAtDesc(Long campusId, Pageable pageable);

    Page<AuditLog> findByCampusIdAndActionOrderByCreatedAtDesc(
            Long campusId, AuditAction action, Pageable pageable);

    Page<AuditLog> findByCampusIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long campusId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable pageable);

    /** Brute-force detection: failed logins for this actor inside a window. */
    long countByActorUserIdAndActionAndCreatedAtAfter(
            Long actorUserId, AuditAction action, LocalDateTime since);
}
