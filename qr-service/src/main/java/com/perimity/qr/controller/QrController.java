package com.perimity.qr.controller;

import com.perimity.qr.dto.ApiResponse;
import com.perimity.qr.dto.BatchProgressResponse;
import com.perimity.qr.dto.JobStatusResponse;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.service.QrRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read endpoints for qr-service, matching the GET rows of the Team
 * Guide's API table (section 4.6).
 *
 * The remaining rows - /download, and the two /api/internal endpoints - need
 * AES token handling, QR/PDF rendering and object storage, which is the Day 8
 * build. Their DTOs already exist in the dto package so the contract is fixed
 * and Palash can code guard-service's caller against it now.
 *
 * @Validated at class level is what makes the @Positive on the path variables
 * below actually run. Without it the annotations are silently ignored - a
 * common and invisible mistake, because nothing warns you.
 */
@RestController
@RequestMapping("/api/qr")
@Validated
@Tag(name = "QR", description = "QR record and generation job lookups")
public class QrController {

    private final QrRecordService qrRecordService;
    private final GenerationJobService generationJobService;

    public QrController(QrRecordService qrRecordService, GenerationJobService generationJobService) {
        this.qrRecordService = qrRecordService;
        this.generationJobService = generationJobService;
    }

    @GetMapping("/jobs/{jobId}/status")
    @Operation(summary = "Get the status of one QR/PDF generation job")
    public ApiResponse<JobStatusResponse> getJobStatus(
            @PathVariable @Positive(message = "jobId must be a positive id") Long jobId) {
        return ApiResponse.ok(generationJobService.getStatus(jobId));
    }

    @GetMapping("/jobs/batch/{batchId}/progress")
    @Operation(summary = "Get generation progress for one bulk-upload batch")
    public ApiResponse<BatchProgressResponse> getBatchProgress(
            @PathVariable @Positive(message = "batchId must be a positive id") Long batchId) {
        return ApiResponse.ok(generationJobService.getBatchProgress(batchId));
    }

    /**
     * Declared last on purpose. Spring ranks a literal path segment above a
     * variable one, so /api/qr/jobs/... and /api/qr/ping both resolve
     * correctly regardless of order - but keeping the catch-all template at
     * the bottom means the file reads the way the router behaves.
     */
    @GetMapping("/{passId}")
    @Operation(summary = "Get the active QR record's object keys and validity for a pass")
    public ApiResponse<QrRecordResponse> getByPassId(
            @PathVariable @Positive(message = "passId must be a positive id") Long passId) {
        return ApiResponse.ok(qrRecordService.getActiveByPassId(passId));
    }
}
