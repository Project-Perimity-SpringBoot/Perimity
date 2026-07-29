package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.response.AuditLogResponse;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.security.CurrentUser;
import com.perimity.auth.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Reading the audit trail.
 *
 * Read-only. There is no POST, PUT or DELETE anywhere on this controller and
 * there must never be one - a trail that can be written to by a client, or
 * edited at all, is not evidence.
 */
@RestController
@RequestMapping("/api/auth/audit")
@Validated
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
@Tag(name = "Audit", description = "Append-only security trail. Read only.")
public class AuditLogController {

    private final AuditQueryService service;
    private final CurrentUser currentUser;

    public AuditLogController(AuditQueryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Audit rows for your campus, newest first")
    public ApiResponse<PageResponse<AuditLogResponse>> byCampus(
            @RequestParam(required = false) AuditAction action,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.byCampus(currentUser.campusId(), action, pageable));
    }

    @GetMapping("/range")
    @Operation(summary = "Audit rows between two timestamps")
    public ApiResponse<PageResponse<AuditLogResponse>> byRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50) Pageable pageable) {

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end of the range must be after the start.");
        }
        return ApiResponse.ok(service.byCampusAndRange(currentUser.campusId(), from, to, pageable));
    }

    @GetMapping("/actor/{actorUserId}")
    @Operation(summary = "Everything one person did. The first query asked after an incident.")
    public ApiResponse<PageResponse<AuditLogResponse>> byActor(
            @PathVariable @Positive Long actorUserId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.byActor(actorUserId, pageable));
    }
}
