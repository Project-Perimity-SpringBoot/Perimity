package com.perimity.guard.controller;

import com.perimity.guard.dto.ApiResponse;
import com.perimity.guard.dto.request.ScanRequestDto;
import com.perimity.guard.dto.response.ScanResponse;
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
 */
@RestController
@RequestMapping("/api/guard")
@Validated
@Tag(name = "Scan", description = "The gate. One scan, one colour, one line of text.")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/scan")
    @Operation(summary = "Scan a QR pass. Always 200 - check scanResult for the answer.")
    public ApiResponse<ScanResponse> scan(@Valid @RequestBody ScanRequestDto dto) {
        ScanResponse result = scanService.scan(dto);
        return ApiResponse.ok(result.message(), result);
    }
}
