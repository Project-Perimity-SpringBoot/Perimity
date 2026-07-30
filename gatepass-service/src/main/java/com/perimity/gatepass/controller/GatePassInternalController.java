package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.HolderPauseDto;
import com.perimity.gatepass.dto.request.PassActivationDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.service.GatePassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service only.
 *
 * SECURITY - Day 7: /api/gatepass/internal/** must sit behind the shared
 * internal API key and must NOT be in the public permit list.
 *
 * Each of these is dangerous in a browser's hands:
 *   activate      - a holder would turn their own pending pass green
 *   pause holder  - anyone could disable a person's access
 */
@RestController
@RequestMapping("/api/gatepass/internal/passes")
@Validated
@Tag(name = "Internal", description = "Service-to-service only. Not for browsers.")
public class GatePassInternalController {

    private final GatePassService service;

    public GatePassInternalController(GatePassService service) {
        this.service = service;
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "qr-service reports generation finished. PENDING to ACTIVE.")
    public ApiResponse<GatePassResponse> activate(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PassActivationDto dto) {

        return ApiResponse.ok("Pass activated", service.activate(id, dto));
    }

    @PostMapping("/holder/{holderUserId}/pause")
    @Operation(summary = "user-service reports a sensitive profile edit. Holds every active pass.")
    public ApiResponse<Map<String, Object>> pauseHolder(
            @PathVariable @Positive Long holderUserId,
            @Valid @RequestBody HolderPauseDto dto) {

        List<GatePassResponse> paused = service.pauseAllForHolder(holderUserId, dto);
        return ApiResponse.ok("Passes paused pending re-approval",
                Map.of("pausedCount", paused.size(), "passes", paused));
    }

    @GetMapping("/holder/{holderUserId}/running-event")
    @Operation(summary = "Behavior 2 - does this person have an event running today?")
    public ApiResponse<Map<String, Object>> runningEvent(
            @PathVariable @Positive Long holderUserId) {

        return ApiResponse.ok(service.runningEventForHolder(holderUserId)
                .map(id -> Map.<String, Object>of("running", true, "eventId", id))
                .orElse(Map.of("running", false)));
    }
}
