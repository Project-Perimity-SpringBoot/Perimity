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

    /**
     * THE ENDPOINT GUARD-SERVICE SCANS AGAINST. Palash has been calling this
     * since Day 9; it did not exist until Day 11, so every scan fell back to
     * his stub or failed outright.
     *
     * Note his client currently uses /api/internal/gatepass/passes/{id} - the
     * segments the other way round. One of us has to move; this service already
     * has three live endpoints under /api/gatepass/internal and the security
     * matcher is written for that prefix, so his client is the cheaper change.
     * Agreed at standup - do not silently add a second mapping to paper over it,
     * because then two URLs are correct and nobody knows which is canonical.
     */
    @GetMapping("/{id}")
    @Operation(summary = "One pass, by id, for a scan. Not campus-scoped - the caller "
            + "has only a token. Behind the internal API key.")
    public ApiResponse<GatePassResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.getForInternal(id));
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

    @PostMapping("/issue")
    @Operation(summary = "Internal service call to issue a pass (e.g. bulk student onboarding).")
    public ApiResponse<GatePassResponse> issueInternal(
            @RequestBody InternalIssueRequest request) {

        com.perimity.gatepass.dto.request.GatePassCreateDto dto =
                com.perimity.gatepass.dto.request.GatePassCreateDto.builder()
                        .holderUserId(request.holderUserId())
                        .holderName(request.holderName())
                        .visitorRequestId(request.visitorRequestId())
                        .passType(passTypeOf(request.passType()))
                        .eventId(request.eventId())
                        .validFrom(request.validFrom() != null ? request.validFrom() : java.time.LocalDate.now())
                        .validTo(request.validTo())
                        .build();
        dto.setCampusId(request.campusId());

        return ApiResponse.ok("Pass issued", service.issue(dto));
    }

    /**
     * A pass type this service does not recognise is a bad request, not a 500.
     *
     * ==================================================================
     *  WHY valueOf ON ITS OWN WAS WRONG HERE
     * ==================================================================
     * PassType.valueOf("Daily") - wrong case, a typo, or a value a newer
     * caller knows and this build does not - throws IllegalArgumentException,
     * which surfaces as a 500. The caller then cannot tell "I sent something
     * invalid" from "gatepass-service is broken", and during a bulk student
     * import that is the difference between one rejected row and an operator
     * concluding the whole service is down.
     *
     * Null still means DAILY, which is what every existing caller relies on:
     * user-service sends "DAILY" explicitly, the visitor flow sends nothing.
     * Only a non-null value that names no known type is refused, and the
     * message lists what is accepted so whoever sent it can fix it.
     *
     * The same reasoning guard-service already applies in the other
     * direction - it deserialises status and passType as String precisely so
     * an unknown value becomes a logged refusal rather than a 500 at a gate.
     */
    private static com.perimity.gatepass.entity.enums.PassType passTypeOf(String value) {
        if (value == null || value.isBlank()) {
            return com.perimity.gatepass.entity.enums.PassType.DAILY;
        }
        try {
            return com.perimity.gatepass.entity.enums.PassType
                    .valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "\"" + value + "\" is not a pass type. Use one of: "
                            + java.util.Arrays.toString(
                                    com.perimity.gatepass.entity.enums.PassType.values()));
        }
    }

    public record InternalIssueRequest(
            Long holderUserId,
            String holderName,
            Long campusId,
            Long visitorRequestId,
            String passType,
            Long eventId,
            java.time.LocalDate validFrom,
            java.time.LocalDate validTo
    ) {}
}
