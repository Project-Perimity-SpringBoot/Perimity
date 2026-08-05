package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.ImportSettingsDto;
import com.perimity.user.dto.response.ImportBatchResponse;
import com.perimity.user.dto.response.ImportSettingsResponse;
import com.perimity.user.service.ImportSettingsService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.perimity.user.dto.response.ImportRowResponse;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.entity.enums.ImportRowOutcome;
import com.perimity.user.service.StudentImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk student onboarding from a Google Form responses sheet.
 *
 * See docs/BULK_STUDENT_ONBOARDING.md.
 *
 * ==========================================================================
 * UPLOAD AND CONFIRM ARE TWO REQUESTS, AND THAT IS THE DESIGN
 * ==========================================================================
 *   POST /import          reads the sheet, writes nothing but the batch
 *   GET  /import/{id}     what it found
 *   GET  /import/{id}/rows the rows, rejected ones first by default
 *   POST /import/{id}/confirm  creates the accounts
 *
 * Nothing is created until a person has seen the preview and pressed confirm.
 * That pause is what makes the import honest: rows land VERIFIED, and
 * verifiedBy records whoever confirmed. Writing straight from an upload would
 * put a named member of staff against details they never saw.
 *
 * It is also the last chance to catch the wrong file - last term's sheet, a
 * different campus, a renamed column - before it becomes two hundred accounts.
 *
 * ==========================================================================
 * WHO MAY DO THIS
 * ==========================================================================
 * The same roles that may create a student one at a time. An import is the
 * same act at volume, so it cannot be a way for someone to do what the single
 * path forbids them.
 *
 * campusId is never a parameter. It comes from the token, so an import always
 * lands on the uploader's own campus.
 */
@RestController
@RequestMapping("/api/user/students/import")
@Validated
@Tag(name = "Student import",
     description = "Bulk onboarding from a Google Form responses sheet")
public class StudentImportController {

    private final StudentImportService importService;
    private final ImportSettingsService settingsService;

    public StudentImportController(StudentImportService importService,
                                   ImportSettingsService settingsService) {
        this.importService = importService;
        this.settingsService = settingsService;
    }

    /**
     * Upload a responses sheet and check it. Creates no accounts.
     *
     * Returns 200 rather than 201 even though a batch row is written: what the
     * caller cares about is the report, and a Location header pointing at a
     * batch that has produced nothing yet would overstate what happened.
     *
     * A batch that comes back FAILED is still a 200. The request was processed
     * and the answer is "this sheet cannot be used, here is why" - which is
     * information, not a server error. Reserving non-2xx for actual failures
     * keeps the frontend's error handling meaningful.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Upload a responses sheet and validate every row. Writes no accounts.")
    public ApiResponse<ImportBatchResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Sheet checked",
                ImportBatchResponse.from(importService.validate(file)));
    }

    /**
     * One batch. Polled by the progress screen while a confirm runs.
     *
     * Campus-scoped in the service - a bare id lookup would let one campus
     * watch another campus's import by guessing a small integer.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "One import batch and its counts")
    public ApiResponse<ImportBatchResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(ImportBatchResponse.from(importService.getBatch(id)));
    }

    /**
     * The rows of a batch.
     *
     * outcome defaults to REJECTED because that is what a person opening this
     * screen wants. A preview of a hundred and ninety-seven fine rows is not
     * worth reading; the three broken ones are the whole reason the preview
     * exists. Pass outcome= explicitly for everything.
     */
    @GetMapping("/{id}/rows")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Rows of a batch. Rejected rows only unless outcome is given.")
    public ApiResponse<PageResponse<ImportRowResponse>> rows(
            @PathVariable @Positive Long id,
            @RequestParam(required = false, defaultValue = "REJECTED") String outcome,
            @PageableDefault(size = 50) Pageable pageable) {

        ImportRowOutcome filter = "ALL".equalsIgnoreCase(outcome)
                ? null
                : ImportRowOutcome.valueOf(outcome.toUpperCase());

        return ApiResponse.ok(importService.rows(id, filter, pageable));
    }

    /**
     * Create the accounts.
     *
     * The endpoint that actually writes, and the one a human has to reach
     * deliberately. Refused unless the batch is VALIDATED, so it cannot run
     * twice and cannot run on a sheet nobody previewed.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Create the accounts and profiles for a validated batch. "
            + "Rows import as verified against you.")
    public ApiResponse<ImportBatchResponse> confirm(@PathVariable @Positive Long id) {
        return ApiResponse.ok("Import finished",
                ImportBatchResponse.from(importService.confirm(id)));
    }

    /* =========================================================
     *  THE INTAKE FORM
     * ========================================================= */

    /**
     * This campus's form link and responses sheet.
     *
     * Returns a blank record rather than 404 when nothing is configured - "not
     * set up yet" is a state the screen renders setup instructions for, not an
     * error.
     */
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "The campus intake form link and its responses sheet")
    public ApiResponse<ImportSettingsResponse> settings() {
        return ApiResponse.ok(ImportSettingsResponse.from(
                settingsService.forCurrentCampus(), importService.driveAvailable()));
    }

    /**
     * Store the form link and the responses sheet.
     *
     * Both may be pasted as whole URLs - nobody should have to know which part
     * of a Google address is the id.
     */
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Save the campus intake form link and responses sheet")
    public ApiResponse<ImportSettingsResponse> saveSettings(
            @Valid @RequestBody ImportSettingsDto dto) {

        return ApiResponse.ok("Form settings saved", ImportSettingsResponse.from(
                settingsService.save(dto.getFormUrl(), dto.getResponsesSheetUrl()),
                importService.driveAvailable()));
    }

    /**
     * Validate the campus's responses sheet straight from Drive.
     *
     * The same parser, validator and preview as an upload - only the source
     * differs. It exists because downloading a file and immediately uploading
     * it again is a round trip the server can do itself, and every manual step
     * is a chance to import last month's copy from a downloads folder.
     */
    @PostMapping("/pull")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Read the latest form responses from Drive and check every row")
    public ApiResponse<ImportBatchResponse> pull() {
        return ApiResponse.ok("Responses checked",
                ImportBatchResponse.from(importService.validateFromDrive()));
    }

    /**
     * The responses sheet as a file, for anyone who wants to look at it in
     * Excel before importing.
     *
     * Not the main path - Pull does the same thing without the round trip - but
     * a person who wants to read two hundred rows in a spreadsheet should not
     * have to go to Drive to do it.
     */
    @GetMapping("/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Download the responses sheet as .xlsx")
    public ResponseEntity<Resource> download() {
        byte[] workbook = importService.downloadResponsesSheet();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"student-responses.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(workbook.length)
                .body(new ByteArrayResource(workbook));
    }

    /** Recent imports on this campus, newest first. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Recent import batches on this campus")
    public ApiResponse<PageResponse<ImportBatchResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(importService.listBatches(pageable));
    }
}
