package com.perimity.guard.controller;

import com.perimity.guard.dto.ApiResponse;
import com.perimity.guard.dto.request.ScanSessionEndDto;
import com.perimity.guard.dto.request.ScanSessionStartDto;
import com.perimity.guard.dto.response.ScanSessionResponse;
import com.perimity.guard.service.ScanSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
 * guardUserId is a body field for now; it comes from the JWT once Omkar's
 * filter lands on Day 7.
 */
@RestController
@RequestMapping("/api/guard/sessions")
@Validated
@Tag(name = "Guard shifts", description = "A guard pins to one gate per shift")
public class ScanSessionController {

    private final ScanSessionService service;

    public ScanSessionController(ScanSessionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Start a shift at one gate")
    public ResponseEntity<ApiResponse<ScanSessionResponse>> start(
            @Valid @RequestBody ScanSessionStartDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Shift started", service.start(dto)));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "Close a shift")
    public ApiResponse<ScanSessionResponse> end(
            @PathVariable @NotBlank String id,
            @Valid @RequestBody ScanSessionEndDto dto) {

        return ApiResponse.ok("Shift ended", service.end(id, dto));
    }

    @GetMapping("/current")
    @Operation(summary = "The guard's open shift")
    public ApiResponse<ScanSessionResponse> current(@RequestParam @Positive Long guardUserId) {
        return ApiResponse.ok(service.current(guardUserId));
    }

    @GetMapping("/open")
    @Operation(summary = "Every guard on duty at this campus right now")
    public ApiResponse<List<ScanSessionResponse>> open(@RequestParam @Positive Long campusId) {
        return ApiResponse.ok(service.openAtCampus(campusId));
    }

    @GetMapping("/history")
    @Operation(summary = "One guard's shift history, newest first")
    public ApiResponse<List<ScanSessionResponse>> history(@RequestParam @Positive Long guardUserId) {
        return ApiResponse.ok(service.history(guardUserId));
    }
}
