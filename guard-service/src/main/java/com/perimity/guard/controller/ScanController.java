package com.perimity.guard.controller;

import com.perimity.guard.dto.ApiResponse;
import com.perimity.guard.dto.request.ScanRequestDto;
import com.perimity.guard.dto.response.ScanResponse;
import com.perimity.guard.security.CurrentUser;
import com.perimity.guard.service.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The gate.
 *
 * Note the response is always 200, even for a refusal. A denied scan is not an
 * HTTP error - it is a successful scan with a negative answer, and the scanner
 * app must render it rather than fall into an error branch.
 *
 * ==========================================================================
 * DAY 7 CHANGE - guardUserId now comes from the token, not the body
 * ==========================================================================
 * Before today the scanner app sent guardUserId in the request JSON. That made
 * every entry log a claim rather than evidence: any caller could post a scan as
 * any guard, at any gate, and the register would record it as fact. The point of
 * replacing the paper register is that the digital one cannot be written by hand.
 *
 * The id now comes from the verified JWT. SecurityConfig restricts this path to
 * hasRole('GUARD'), so by the time this method runs the caller is a guard;
 * requireOpenSession in ScanService then proves they are actually on shift.
 * Role plus session is the full Day 7 gate for this service.
 */
@RestController
@RequestMapping("/api/guard")
@Validated
@Tag(name = "Scan", description = "The gate. One scan, one colour, one line of text.")
public class ScanController {

    private final ScanService scanService;
    private final CurrentUser currentUser;

    public ScanController(ScanService scanService, CurrentUser currentUser) {
        this.scanService = scanService;
        this.currentUser = currentUser;
    }

    @PostMapping("/scan")
    @Operation(summary = "Scan a QR pass. Always 200 - check scanResult for the answer.")
    public ApiResponse<ScanResponse> scan(@Valid @RequestBody ScanRequestDto dto) {
        ScanResponse result = scanService.scan(dto, currentUser.userId());
        return ApiResponse.ok(result.message(), result);
    }
}
