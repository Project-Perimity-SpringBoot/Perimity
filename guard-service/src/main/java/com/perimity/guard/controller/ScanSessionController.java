package com.perimity.guard.controller;

import com.perimity.guard.dto.ApiResponse;
import com.perimity.guard.dto.request.ScanSessionStartDto;
import com.perimity.guard.dto.response.ScanSessionResponse;
import com.perimity.guard.security.CurrentUser;
import com.perimity.guard.service.ScanSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Guard shifts.
 *
 * Every body carries @Valid. Without it none of the DTO constraints run and the
 * endpoint silently accepts anything - including a gate name with a newline in
 * it, which is the log-forging case the pattern fix closes.
 *
 * ==========================================================================
 * DAY 7 - IDENTITY NOW COMES FROM THE TOKEN, NEVER FROM THE CLIENT
 * ==========================================================================
 * guardUserId used to be a body field and campusId a query parameter. Both are
 * now read from the verified JWT:
 *
 *   - a guard cannot open, close, or read a shift in another guard's name
 *   - a Campus Admin cannot read another institution's guards by editing a URL
 *
 * The second one is the multi-tenant promise in SRS 2.1. A system that scopes by
 * a number the caller supplies is not multi-tenant, it is single-tenant with
 * extra steps.
 */
@RestController
@RequestMapping("/api/guard/sessions")
@Validated
@Tag(name = "Guard shifts", description = "A guard pins to one gate per shift")
public class ScanSessionController {

    private final ScanSessionService service;
    private final CurrentUser currentUser;

    public ScanSessionController(ScanSessionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Start a shift at one gate")
    public ResponseEntity<ApiResponse<ScanSessionResponse>> start(
            @Valid @RequestBody ScanSessionStartDto dto) {

        // Both identity and campus come from the verified token. The body only
        // says which gate. See the note on GET /open below - this is the same
        // rule, applied to the write that everything else inherits from.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Shift started",
                        service.start(dto, currentUser.userId(), currentUser.campusId())));
    }

    /**
     * No request body. The path says which shift, the token says who is closing
     * it. An empty body would only invite someone to put an id back in it.
     */
    @PostMapping("/{id}/end")
    @Operation(summary = "Close a shift")
    public ApiResponse<ScanSessionResponse> end(@PathVariable @NotBlank String id) {
        return ApiResponse.ok("Shift ended", service.end(id, currentUser.userId()));
    }

    @GetMapping("/current")
    @Operation(summary = "Your own open shift")
    public ApiResponse<ScanSessionResponse> current() {
        return ApiResponse.ok(service.current(currentUser.userId()));
    }

    /**
     * Campus comes from the token, never a parameter. As a query parameter, a
     * Campus Admin of one institution could list another's guards on duty just
     * by changing the number.
     */
    @GetMapping("/open")
    @Operation(summary = "Every guard on duty at your campus right now")
    public ApiResponse<List<ScanSessionResponse>> open() {
        return ApiResponse.ok(service.openAtCampus(currentUser.campusId()));
    }

    @GetMapping("/history")
    @Operation(summary = "Your own shift history, newest first")
    public ApiResponse<List<ScanSessionResponse>> history() {
        return ApiResponse.ok(service.history(currentUser.userId()));
    }
}
