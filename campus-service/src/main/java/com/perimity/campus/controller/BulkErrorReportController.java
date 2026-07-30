package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.request.BulkErrorReportDto;
import com.perimity.campus.dto.response.BulkErrorReportResponse;
import com.perimity.campus.security.CurrentUser;
import com.perimity.campus.service.BulkErrorReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bulk-upload error report. Day 10, FR-BULK-9.
 *
 * TWO ENDPOINTS ON TWO DIFFERENT DOORS, and that split is the whole design:
 *
 *   POST  /api/campus/internal/... service-to-service, X-Internal-Api-Key.
 *                                  gatepass-service is the only writer.
 *   GET   /api/campus/campuses/... a person, holding a JWT, confined to their
 *                                  own campus.
 *
 * They are deliberately not one resource with two methods. A person must never
 * be able to write an error report - it is evidence about a batch, and one that
 * a human could author is worth nothing. A service must never need a JWT,
 * because it has no user to be.
 */
@RestController
@Validated
@Tag(name = "Bulk error report", description = "Failed rows of a bulk upload, as a CSV")
public class BulkErrorReportController {

    private final BulkErrorReportService service;
    private final CurrentUser currentUser;

    public BulkErrorReportController(BulkErrorReportService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /**
     * Called by gatepass-service the moment validation finishes, before the
     * uploader has decided whether to confirm.
     *
     * Returns the key, which the caller stores on its bulk_upload_batches row.
     * It does not return a URL: nobody is waiting to read the file at this
     * point, and a signed URL minted now would have expired by the time the
     * uploader clicks Download.
     */
    @PostMapping("/api/campus/internal/campuses/{campusId}/bulk/{batchId}/error-report")
    @Operation(summary = "Store the failed rows of a batch as a CSV. Service-to-service only.")
    public ApiResponse<BulkErrorReportResponse> store(
            @PathVariable @Positive Long campusId,
            @PathVariable @Positive Long batchId,
            @Valid @RequestBody BulkErrorReportDto dto) {

        return ApiResponse.ok("Error report stored", service.store(campusId, batchId, dto));
    }

    /**
     * The uploader clicking Download on the bulk progress screen.
     *
     * Faculty as well as admins, because Faculty is who uploads. requireSameCampus
     * is what a role annotation cannot say: hasRole('FACULTY') permits the call,
     * it cannot stop a faculty member of campus 2 reading campus 1's report by
     * editing the id in the URL - and this file lists the email addresses of
     * everyone whose registration failed.
     */
    @GetMapping("/api/campus/campuses/{campusId}/bulk/{batchId}/error-report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "A short-lived link to this batch's error report. "
            + "404 when the batch had no failed rows.")
    public ApiResponse<BulkErrorReportResponse> download(
            @PathVariable @Positive Long campusId,
            @PathVariable @Positive Long batchId) {

        currentUser.requireSameCampus(campusId);
        return ApiResponse.ok(service.downloadUrl(campusId, batchId));
    }
}
