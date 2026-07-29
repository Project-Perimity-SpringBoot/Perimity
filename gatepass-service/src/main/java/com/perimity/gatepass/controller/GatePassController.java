package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.GatePassCreateDto;
import com.perimity.gatepass.dto.request.GatePassStatusUpdateDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.service.GatePassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Gate passes.
 *
 * Every request body carries @Valid. Without it none of the DTO constraints run
 * and the endpoint silently accepts anything.
 *
 * campusId and holderUserId are request parameters for now. They come from the
 * JWT once Omkar's filter lands on Day 7.
 */
@RestController
@RequestMapping("/api/gatepass/passes")
@Validated
@Tag(name = "Gate passes", description = "Issue passes and move them through the lifecycle")
public class GatePassController {

    private final GatePassService service;

    public GatePassController(GatePassService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Issue a pass. Created PENDING; the QR pipeline activates it.")
    public ResponseEntity<ApiResponse<GatePassResponse>> issue(
            @Valid @RequestBody GatePassCreateDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pass issued", service.issue(dto)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Pause, resume or revoke. Checked against the state machine.")
    public ApiResponse<GatePassResponse> changeStatus(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId,
            @Valid @RequestBody GatePassStatusUpdateDto dto) {

        return ApiResponse.ok("Pass updated", service.changeStatus(campusId, id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One pass")
    public ApiResponse<GatePassResponse> getOne(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(service.getOne(campusId, id));
    }

    @GetMapping("/holder/{holderUserId}")
    @Operation(summary = "Every pass this person holds. The wallet screen.")
    public ApiResponse<List<GatePassResponse>> byHolder(
            @PathVariable @Positive Long holderUserId) {

        return ApiResponse.ok(service.byHolder(holderUserId));
    }

    @GetMapping("/holder/{holderUserId}/active")
    @Operation(summary = "Only the passes that would open a gate right now")
    public ApiResponse<List<GatePassResponse>> activeByHolder(
            @PathVariable @Positive Long holderUserId) {

        return ApiResponse.ok(service.activeByHolder(holderUserId));
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Every pass issued for one event")
    public ApiResponse<List<GatePassResponse>> byEvent(
            @PathVariable @Positive Long eventId) {

        return ApiResponse.ok(service.byEvent(eventId));
    }

    @GetMapping("/count")
    @Operation(summary = "How many passes a campus holds in one status")
    public ApiResponse<Map<String, Long>> count(
            @RequestParam @Positive Long campusId,
            @RequestParam(defaultValue = "ACTIVE") PassStatus status) {

        return ApiResponse.ok(Map.of(status.name(), service.countByStatus(campusId, status)));
    }
}
