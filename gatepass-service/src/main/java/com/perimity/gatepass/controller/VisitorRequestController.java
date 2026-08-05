package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import com.perimity.gatepass.dto.request.VisitorRequestDecisionDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.dto.response.VisitorRequestResponse;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.security.CurrentUser;
import com.perimity.gatepass.service.VisitorRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * The visitor workflow.
 *
 * Role split:
 *   submit    - any signed-in user. A visitor holds a token after OTP login.
 *   decide    - FACULTY and up. This is the approval authority.
 *   cancel    - the visitor themself, or staff.
 *   queues    - staff only.
 *   by-email  - your own address only, unless you are staff.
 */
@RestController
@RequestMapping("/api/gatepass/visitor-requests")
@Validated
@Tag(name = "Visitor requests", description = "Submit, approve, reject and track visitor entry requests")
public class VisitorRequestController {

    private final VisitorRequestService service;
    private final CurrentUser currentUser;

    public VisitorRequestController(VisitorRequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "A visitor submits the registration form")
    public ResponseEntity<ApiResponse<VisitorRequestResponse>> submit(
            @Valid @RequestBody VisitorRequestCreateDto dto) {

        // The visitor chooses the campus now - see VisitorRequestCreateDto for
        // why that is safe. The service checks it exists; the rule lives next
        // to the row it is about, as the ownership checks do.

        // A visitor here is signed in, and a visitor signs in only by email OTP.
        // Their address is already proven, so the request is created verified.
        Long verifiedVisitor = currentUser.require().isVisitor() ? currentUser.userId() : null;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request submitted", service.submit(dto, verifiedVisitor)));
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "The host approves or rejects. An approval issues a PENDING pass.")
    public ApiResponse<VisitorRequestResponse> decide(
            @PathVariable @Positive Long id,
            @Valid @RequestBody VisitorRequestDecisionDto dto) {

        // The reviewer is whoever holds the token, not whoever the body names.
        // Otherwise one faculty member could record an approval under another's
        // name, and the audit trail would be worthless.
        dto.setReviewedBy(currentUser.userId());

        return ApiResponse.ok("Decision recorded", service.decide(currentUser.campusId(), id, dto));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "The visitor withdraws a request that has not been decided")
    public ApiResponse<VisitorRequestResponse> cancel(@PathVariable @Positive Long id) {
        return ApiResponse.ok("Request cancelled", service.cancel(currentUser.campusId(), id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One request")
    public ApiResponse<VisitorRequestResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.getOne(currentUser.campusId(), id));
    }

    @GetMapping("/{id}/pass")
    @Operation(summary = "The pass issued for an approved request")
    public ApiResponse<GatePassResponse> passFor(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.passFor(currentUser.campusId(), id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "The campus queue. Oldest first, because it is a queue.")
    public ApiResponse<PageResponse<VisitorRequestResponse>> byCampus(
            @RequestParam(defaultValue = "PENDING") RequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ApiResponse.ok(service.byCampusAndStatus(currentUser.campusId(), status, pageable));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "The approval queue for your campus")
    public ApiResponse<PageResponse<VisitorRequestResponse>> myQueue(
            @RequestParam(defaultValue = "PENDING") RequestStatus status,
            @RequestParam(required = false) @Positive Long campusId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        /*
         * Campus-wide, not per-host.
         *
         * A visitor now chooses a campus rather than naming a person, so a
         * per-host queue would leave every request unseen: hostUserId is
         * usually null. Any faculty of the campus can act on it, and decide()
         * records who did in reviewedBy.
         *
         * campusId comes from the token for faculty and campus admins, exactly
         * as hostUserId used to. The ?campusId= parameter exists only because a
         * Super Admin has no campus of their own and must name one; supplying
         * someone else's is refused, not ignored. See
         * CurrentUser.resolveCampusForListing.
         */
        return ApiResponse.ok(service.byCampusAndStatus(
                currentUser.resolveCampusForListing(campusId), status, pageable));
    }

    @GetMapping("/my-history")
    @Operation(summary = "Your own request history, keyed by the email in your token")
    public ApiResponse<List<VisitorRequestResponse>> myHistory() {
        return ApiResponse.ok(service.byEmail(currentUser.require().email()));
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Look up anyone's history. Staff only - see /my-history for your own.")
    public ApiResponse<List<VisitorRequestResponse>> byEmail(@RequestParam String email) {
        return ApiResponse.ok(service.byEmail(email));
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "How many requests are waiting, for the dashboard badge")
    public ApiResponse<Map<String, Long>> pendingCount() {
        return ApiResponse.ok(Map.of("pending", service.countPending(currentUser.campusId())));
    }
}
