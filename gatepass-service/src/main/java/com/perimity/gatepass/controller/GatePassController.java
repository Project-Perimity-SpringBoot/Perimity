package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.GatePassCreateDto;
import com.perimity.gatepass.dto.request.GatePassStatusUpdateDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.security.CurrentUser;
import com.perimity.gatepass.service.GatePassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Gate passes.
 *
 * Note /passes/mine below. A student reading their own wallet needs no id in
 * the URL at all - the token already says who they are. Fewer parameters, fewer
 * ways to read someone else's data.
 */
@RestController
@RequestMapping("/api/gatepass/passes")
@Validated
@Tag(name = "Gate passes", description = "Issue passes and move them through the lifecycle")
public class GatePassController {

    private final GatePassService service;
    private final CurrentUser currentUser;

    public GatePassController(GatePassService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Issue a pass. Created PENDING; the QR pipeline activates it.")
    public ResponseEntity<ApiResponse<GatePassResponse>> issue(
            @Valid @RequestBody GatePassCreateDto dto) {

        dto.setCampusId(currentUser.campusId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pass issued", service.issue(dto)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Pause, resume or revoke. Checked against the state machine.")
    public ApiResponse<GatePassResponse> changeStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody GatePassStatusUpdateDto dto) {

        dto.setChangedBy(currentUser.userId());
        return ApiResponse.ok("Pass updated", service.changeStatus(currentUser.campusId(), id, dto));
    }

    @PostMapping("/{id}/republish")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Re-queue QR generation for a pass stuck at PENDING")
    public ApiResponse<GatePassResponse> republish(@PathVariable @Positive Long id) {
        return ApiResponse.ok("Generation job re-queued",
                service.republishGenerationJob(currentUser.campusId(), id));
    }

    @GetMapping("/mine")
    @Operation(summary = "Your own wallet. No id needed - the token says who you are.")
    public ApiResponse<List<GatePassResponse>> mine() {
        return ApiResponse.ok(service.byHolder(currentUser.userId()));
    }

    @GetMapping("/mine/active")
    @Operation(summary = "Only your passes that would open a gate right now")
    public ApiResponse<List<GatePassResponse>> mineActive() {
        return ApiResponse.ok(service.activeByHolder(currentUser.userId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One pass on your campus")
    public ApiResponse<GatePassResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.getOne(currentUser.campusId(), id));
    }

    @GetMapping("/holder/{holderUserId}")
    @Operation(summary = "Someone's passes. Staff only, or your own.")
    public ApiResponse<List<GatePassResponse>> byHolder(
            @PathVariable @Positive Long holderUserId) {

        // The check a role annotation cannot express. hasRole('STUDENT') says a
        // student may call this; it cannot say WHOSE passes they may read.
        currentUser.requireSelfOrStaff(holderUserId);
        return ApiResponse.ok(service.byHolder(holderUserId));
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Every pass issued for one event")
    public ApiResponse<List<GatePassResponse>> byEvent(@PathVariable @Positive Long eventId) {
        return ApiResponse.ok(service.byEvent(eventId));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "How many passes your campus holds in one status")
    public ApiResponse<Map<String, Long>> count(
            @RequestParam(defaultValue = "ACTIVE") PassStatus status) {

        return ApiResponse.ok(Map.of(status.name(),
                service.countByStatus(currentUser.campusId(), status)));
    }
}
