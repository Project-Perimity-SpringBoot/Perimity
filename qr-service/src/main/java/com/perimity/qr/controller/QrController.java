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
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.perimity.qr.security.PerimityPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read endpoints for qr-service, matching the GET rows of the Team
 * Guide's API table (section 4.6).
 *
 * The service-to-service rows live in QrInternalController, behind the shared
 * internal API key.
 *
 * Everything in this class requires a Bearer token - see SecurityConfig. It is
 * authenticated but NOT ownership-scoped: a signed-in student can still read
 * another holder's QR keys by changing the id. Closing that needs the holder's
 * identity, which lives in gatepass-service, so it is a cross-service call on a
 * read path rather than a matcher.
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
     * The visitor's "Download PDF" action, and the student's equivalent.
     *
     * Returns the bytes rather than the pdfKey. QrRecordResponse already
     * carries pdfKey, but that is an object-storage key, not a URL - the
     * browser can do nothing with it, and handing out anything it could
     * dereference directly would mean a public bucket. The service reads the
     * object and this streams it, so storage stays private and moving to S3 on
     * Day 22 changes nothing on this side.
     *
     * no-store, not the default. A pass PDF contains the QR whose token is the
     * entry credential; a copy left in a shared proxy cache or a phone's disk
     * cache is a working pass for whoever finds it.
     *
     * Declared before /{passId} for readability only. Spring ranks by
     * specificity, not declaration order, so a two-segment template outranks
     * the one-segment one regardless - but the file should read the way the
     * router behaves.
     */
    @GetMapping(value = "/{passId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the pass PDF for a pass")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable @Positive(message = "passId must be a positive id") Long passId,
            @AuthenticationPrincipal PerimityPrincipal caller) {

        // The service decides whether this caller may have these bytes. Passing
        // the principal rather than checking here keeps the rule next to the
        // row it is about - see QrRecordService.download.
        byte[] pdf = qrRecordService.download(passId, true, caller);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("pass-" + passId + ".pdf")
                                .build()
                                .toString())
                .body(pdf);
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
