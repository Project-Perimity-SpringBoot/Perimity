package com.perimity.auth.dto.request;

import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/internal/auth/audit-events.
 *
 * One security-relevant thing that happened in another service. Today that is a
 * guard opening or closing a shift; the same endpoint takes an approval, a
 * rejection, a revocation or a config change when those services want to record
 * one.
 *
 * WHY THIS EXISTS AT ALL: audit_logs lives in AuthDB, and no service reads
 * another service's database. So a shift starting in guard-service can only
 * reach the audit trail by being posted here. The alternative - a second audit
 * table per service - would mean the Campus Admin's audit screen has to query
 * six places and merge them, and FR-AUD-3 asks for one searchable, paginated
 * view.
 *
 * sourceIp is the ORIGINAL caller's address, forwarded by the calling service.
 * If it were left out, every row would carry that service's container IP.
 */
@Schema(description = "A security-relevant event that happened in another service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalAuditEventDto {

    /**
     * Not every action is postable - see the allowlist on the controller. An
     * unknown or refused value is a 400, never a silently dropped row: a caller
     * that thinks it recorded something and did not is worse than an error.
     */
    @NotNull(message = "Action is required")
    @Schema(description = "SHIFT_STARTED, SHIFT_ENDED, REQUEST_APPROVED, REQUEST_REJECTED, "
            + "PASS_REVOKED or CAMPUS_CONFIG_CHANGED", example = "SHIFT_STARTED")
    private AuditAction action;

    @NotNull(message = "The acting user is required")
    @Positive
    @Schema(description = "Who did it. From the caller's JWT, never from its request body.",
            example = "42")
    private Long actorUserId;

    @NotNull(message = "The acting role is required")
    @Schema(description = "That user's role, also from their token", example = "GUARD")
    private Role actorRole;

    @NotNull(message = "Campus is required")
    @Positive
    @Schema(description = "Scopes the Campus Admin's view of this row", example = "1")
    private Long campusId;

    /**
     * The convention is "type:id" - gate:7, pass:412, session:66f1a2. It is what
     * makes the audit log searchable for one object's whole history, so it is
     * worth being consistent about even though nothing enforces the shape.
     */
    @Size(max = 120)
    @Schema(description = "What was acted on, as type:id", example = "gate:7")
    private String targetEntity;

    @Size(max = 500)
    @Schema(description = "Human-readable context. NEVER a password, OTP or token "
            + "(FR-AUD-5).", example = "Shift started at North Gate", nullable = true)
    private String details;

    @Pattern(regexp = ValidationPatterns.IP_ADDRESS, message = ValidationPatterns.IP_ADDRESS_MESSAGE)
    @Schema(description = "The ORIGINAL client's IP, forwarded by the calling service. "
            + "Omit and the row records the calling service's own address instead.",
            example = "10.4.2.88", nullable = true)
    private String sourceIp;
}
