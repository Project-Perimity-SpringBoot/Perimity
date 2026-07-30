package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.InternalAuditEventDto;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The write door into the audit trail, for the other five services. Day 11.
 *
 * AuditLogController is the read side: admin-only, JWT, GET only. This is the
 * write side: service-to-service, X-Internal-Api-Key, POST only. They are
 * deliberately different controllers on different doors, because a person must
 * never be able to write an audit row and a service has no JWT to read one with.
 *
 * Still append-only. There is no PUT and no DELETE here or anywhere else
 * (FR-AUD-4), and adding one would make the whole table worthless as evidence.
 */
@RestController
@RequestMapping("/api/internal/auth/audit-events")
@Validated
@SecurityRequirement(name = "internalApiKey")
@Tag(name = "Internal - Audit", description = "Other services record events here")
public class InternalAuditController {

    /**
     * THE IMPORTANT LINE IN THIS FILE.
     *
     * Only actions that genuinely happen elsewhere may be posted. Everything
     * auth-service observes itself - logins, logouts, OTPs, password changes,
     * account and blocklist changes - is refused, because a row saying
     * "LOGIN_SUCCESS, user 42" must only ever be written by the code that
     * actually checked user 42's password.
     *
     * Without this, the shared internal key would be enough to forge a clean
     * login history for any account, on the very table an investigation would
     * consult first. The key is shared by six services and sits in a .env file
     * on six laptops; treating it as sufficient authority to write anything
     * would put the audit trail exactly as far out of reach as the weakest
     * teammate's machine.
     */
    private static final Set<AuditAction> POSTABLE = EnumSet.of(
            AuditAction.SHIFT_STARTED,
            AuditAction.SHIFT_ENDED,
            AuditAction.REQUEST_APPROVED,
            AuditAction.REQUEST_REJECTED,
            AuditAction.PASS_REVOKED,
            AuditAction.CAMPUS_CONFIG_CHANGED);

    private final AuditService audit;

    public InternalAuditController(AuditService audit) {
        this.audit = audit;
    }

    /**
     * 201, and a body with nothing in it worth reading.
     *
     * The caller must not need this response. guard-service should post it and
     * carry on - a shift that cannot start because the audit service is slow is
     * a guard standing at a gate unable to work, which trades a much larger
     * failure for a much smaller one.
     */
    @PostMapping
    @Operation(summary = "Record an event that happened in another service. "
            + "Only cross-service actions are accepted.")
    public ResponseEntity<ApiResponse<Void>> record(@Valid @RequestBody InternalAuditEventDto dto) {

        if (!POSTABLE.contains(dto.getAction())) {
            // Names the action so a teammate debugging their own call sees the
            // problem immediately. Nothing is leaked: the caller already knew
            // which action it sent.
            throw new IllegalArgumentException(
                    dto.getAction() + " is recorded by auth-service itself and cannot be "
                            + "posted from another service. Postable actions: " + POSTABLE + ".");
        }

        audit.recordFromService(
                dto.getAction(),
                dto.getActorUserId(),
                dto.getActorRole(),
                dto.getCampusId(),
                dto.getTargetEntity(),
                dto.getDetails(),
                dto.getSourceIp());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Recorded", null));
    }
}
