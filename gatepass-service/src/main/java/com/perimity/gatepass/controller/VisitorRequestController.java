package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.VisitorEmailVerifiedDto;
import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import com.perimity.gatepass.dto.request.VisitorRequestDecisionDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.dto.response.VisitorRequestResponse;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.service.VisitorRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * The visitor workflow: submit, verify, decide, cancel.
 *
 * Every request body carries @Valid, or none of the DTO constraints run and the
 * endpoint silently accepts anything.
 *
 * campusId and hostUserId are request parameters for now. They come from the
 * JWT once Omkar's filter lands on Day 7, and these parameters disappear.
 */
@RestController
@RequestMapping("/api/gatepass/visitor-requests")
@Validated
@Tag(name = "Visitor requests", description = "Submit, approve, reject and track visitor entry requests")
public class VisitorRequestController {

    private final VisitorRequestService service;

    public VisitorRequestController(VisitorRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "A visitor submits the registration form")
    public ResponseEntity<ApiResponse<VisitorRequestResponse>> submit(
            @Valid @RequestBody VisitorRequestCreateDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request submitted", service.submit(dto)));
    }

    @PatchMapping("/{id}/decision")
    @Operation(summary = "The host approves or rejects. An approval issues a PENDING pass.")
    public ApiResponse<VisitorRequestResponse> decide(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId,
            @Valid @RequestBody VisitorRequestDecisionDto dto) {

        return ApiResponse.ok("Decision recorded", service.decide(campusId, id, dto));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "The visitor withdraws a request that has not been decided")
    public ApiResponse<VisitorRequestResponse> cancel(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok("Request cancelled", service.cancel(campusId, id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One request")
    public ApiResponse<VisitorRequestResponse> getOne(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(service.getOne(campusId, id));
    }

    @GetMapping("/{id}/pass")
    @Operation(summary = "The pass issued for an approved request")
    public ApiResponse<GatePassResponse> passFor(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(service.passFor(campusId, id));
    }

    @GetMapping
    @Operation(summary = "The campus queue. Oldest first, because it is a queue.")
    public ApiResponse<PageResponse<VisitorRequestResponse>> byCampus(
            @RequestParam @Positive Long campusId,
            @RequestParam(defaultValue = "PENDING") RequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ApiResponse.ok(service.byCampusAndStatus(campusId, status, pageable));
    }

    @GetMapping("/for-host")
    @Operation(summary = "One host's own approval queue")
    public ApiResponse<PageResponse<VisitorRequestResponse>> byHost(
            @RequestParam @Positive Long hostUserId,
            @RequestParam(defaultValue = "PENDING") RequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ApiResponse.ok(service.byHostAndStatus(hostUserId, status, pageable));
    }

    @GetMapping("/by-email")
    @Operation(summary = "A visitor's own history, keyed by email")
    public ApiResponse<List<VisitorRequestResponse>> byEmail(
            @RequestParam @NotBlank @Email String email) {

        return ApiResponse.ok(service.byEmail(email));
    }

    @GetMapping("/pending-count")
    @Operation(summary = "How many requests are waiting, for the dashboard badge")
    public ApiResponse<Map<String, Long>> pendingCount(
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(Map.of("pending", service.countPending(campusId)));
    }
}
