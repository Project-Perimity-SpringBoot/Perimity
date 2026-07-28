package com.perimity.auth.dto.response;

import com.perimity.auth.entity.AuditLog;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import java.time.LocalDateTime;

/**
 * Read model for one audit row. Append-only, so there is no updatedAt.
 *
 * A reminder that belongs next to this shape: never put a password, an OTP or a
 * token in the details field, not even hashed. An audit log is the one table
 * most likely to be exported, emailed and pasted into a ticket.
 */
public record AuditLogResponse(
        Long id,
        Long actorUserId,
        Role actorRole,
        AuditAction action,
        String targetEntity,
        Long campusId,
        String sourceIp,
        String details,
        LocalDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog e) {
        return new AuditLogResponse(
                e.getId(),
                e.getActorUserId(),
                e.getActorRole(),
                e.getAction(),
                e.getTargetEntity(),
                e.getCampusId(),
                e.getSourceIp(),
                e.getDetails(),
                e.getCreatedAt()
        );
    }
}
