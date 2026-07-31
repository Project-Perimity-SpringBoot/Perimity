package com.perimity.gatepass.controller;

import com.perimity.gatepass.bulk.TemplateWriter;
import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.BulkConfirmDto;
import com.perimity.gatepass.dto.response.BulkUploadBatchResponse;
import com.perimity.gatepass.dto.response.BulkValidationSummaryResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.security.CurrentUser;
import com.perimity.gatepass.service.BulkUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The bulk upload API.
 *
 * ==========================================================================
 *  THESE ENDPOINTS BACK ARHAM'S SCREENS 9 AND 10. Agree changes with him.
 * ==========================================================================
 *
 *   Screen 9  Bulk Upload    POST /validate, then POST /{id}/confirm
 *   Screen 10 Bulk Progress  GET  /{id}, polled every 2 seconds
 *
 * Screen 10 also polls qr-service's GET /api/qr/jobs/batch/{batchId}/progress
 * for per-job detail. The two are complementary: this service knows how many
 * passes were CREATED, qr-service knows how many were GENERATED and EMAILED.
 * Neither can answer both, which is correct under database-per-service.
 *
 * campusId and uploadedBy are never request parameters. Both come from the JWT,
 * so nobody bulk-loads 600 visitors into another institution by editing a form
 * field.
 *
 * Faculty may upload. Only Campus Admin and above may see other people's
 * batches - list() is deliberately staff-wide because the admin who has to
 * explain a stuck batch needs to be able to find it.
 */
@RestController
@RequestMapping("/api/gatepass/bulk")
@Validated
@Tag(name = "Bulk upload", description = "Two-phase spreadsheet onboarding for students and event visitors")
public class BulkUploadController {

    private final BulkUploadService service;
    private final TemplateWriter templateWriter;
    private final CurrentUser currentUser;

    public BulkUploadController(BulkUploadService service,
                                TemplateWriter templateWriter,
                                CurrentUser currentUser) {
        this.service = service;
        this.templateWriter = templateWriter;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ phase one

    /**
     * Phase one. Upload the sheet and get "580 valid, 20 errors" back in about
     * two seconds. Nothing is created yet.
     *
     * Multipart rather than JSON because a file is being sent. That means
     * BulkUploadInitDto's cross-field @AssertTrue checks never run on this
     * path, so BulkUploadService restates those two rules itself - do not
     * remove them assuming the DTO covers it.
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Upload and validate a sheet. Creates nothing - returns a summary to confirm.")
    public ApiResponse<BulkValidationSummaryResponse> validate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("passType") PassType passType,
            @RequestParam(value = "eventId", required = false) Long eventId) {

        return ApiResponse.ok("Validation complete",
                service.validate(file,
                        currentUser.campusId(),
                        currentUser.userId(),
                        passType,
                        eventId));
    }

    // ------------------------------------------------------------ phase two

    /**
     * Phase two. Release the validated batch to the queue.
     *
     * Returns immediately. The uploader can close the browser; generation
     * continues on RabbitMQ.
     */
    @PostMapping("/{batchId}/confirm")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Confirm a validated batch. Creates identities and passes, queues generation.")
    public ApiResponse<BulkUploadBatchResponse> confirm(
            @PathVariable @Positive Long batchId,
            @Valid @RequestBody BulkConfirmDto dto) {

        // The token is the authority on who confirmed this, not the body.
        dto.setConfirmedBy(currentUser.userId());

        return ApiResponse.ok("Batch released - passes are being generated",
                service.confirm(currentUser.campusId(), batchId, dto));
    }

    /**
     * Retry only the rows that never finished. Screen 10's "retry failed rows"
     * button.
     *
     * Deliberately NOT "re-upload the sheet". Re-uploading creates a second
     * pass for everyone who was already fine.
     */
    @PostMapping("/{batchId}/retry")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Re-queue only the passes of this batch still stuck at PENDING")
    public ApiResponse<Map<String, Object>> retry(@PathVariable @Positive Long batchId) {
        return ApiResponse.ok(service.retryFailedRows(currentUser.campusId(), batchId));
    }

    // ---------------------------------------------------------------- reads

    /** Screen 10 polls this every two seconds. */
    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "One batch, with percentComplete for the progress bar")
    public ApiResponse<BulkUploadBatchResponse> getOne(@PathVariable @Positive Long batchId) {
        return ApiResponse.ok(service.getOne(currentUser.campusId(), batchId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Upload history for this campus, newest first")
    public ApiResponse<PageResponse<BulkUploadBatchResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ApiResponse.ok(service.list(currentUser.campusId(), pageable));
    }

    /**
     * A short-lived link to errors.csv rather than the file itself.
     *
     * Same pattern as Arham's logo endpoint: the bucket stays private and the
     * link expires. Streaming the bytes through this service instead would
     * work locally and then need rewriting the day storage moves to S3.
     */
    @GetMapping("/{batchId}/errors")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Short-lived download link for the row-by-row error report")
    public ApiResponse<Map<String, String>> errorReport(@PathVariable @Positive Long batchId) {
        return ApiResponse.ok(Map.of(
                "url", service.errorReportUrl(currentUser.campusId(), batchId)));
    }

    // ------------------------------------------------------------- template

    /**
     * The blank sheet to fill in.
     *
     * Generated rather than served from a static file, so the columns can never
     * drift from what SheetParser actually reads. A template checked into the
     * repo is a template that goes stale the first time a column changes.
     *
     * Not campus-scoped and needs no role beyond being logged in - it is a
     * blank form with no data in it.
     */
    @GetMapping("/template")
    @Operation(summary = "Download the .xlsx template for a student or event visitor batch")
    public ResponseEntity<Resource> template(
            @RequestParam(value = "passType", defaultValue = "EVENT") PassType passType) {

        byte[] bytes = templateWriter.write(passType);
        String filename = templateWriter.filenameFor(passType);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(TemplateWriter.CONTENT_TYPE))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
