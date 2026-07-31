package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.DocumentVerificationDto;
import com.perimity.user.dto.response.DocumentResponse;
import com.perimity.user.dto.response.PresignedUrlResponse;
import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents attached to a person.
 *
 * The upload takes the FILE. It does not take a storage key - that is generated
 * server-side from the holder's profile, so an upload can only ever land under
 * that person's own prefix (SRS v1.1). The JSON variant that accepted an s3Key
 * is gone; see DocumentService for what it allowed.
 *
 * Nothing here returns file bytes or a permanent URL. Reading a file is a
 * separate call that mints a link valid for minutes, because a permanent link
 * to somebody's ID proof is readable by anyone who ever sees the JSON.
 *
 * Verification is administrative. A Faculty member is staff for the purpose of
 * reading a profile, but approving an identity document is a Campus Admin or
 * Super Admin act, which is why the @PreAuthorize lists below differ.
 */
@RestController
@RequestMapping("/api/user/documents")
@Validated
@Tag(name = "Documents", description = "Upload, verification state and short-lived read links")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    /**
     * multipart/form-data, not JSON. Three parts: the file, who it belongs to,
     * and what kind of document it is.
     *
     * userId is a form field rather than a path variable so the whole upload is
     * one request. The ownership check is unchanged - a student may upload only
     * for themselves.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document. PDF, PNG or JPEG. The storage key is generated here.")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam @NotNull DocumentType docType,
            @RequestPart("file") MultipartFile file) {

        DocumentResponse created = documentService.upload(userId, docType, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Document uploaded and awaiting verification", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one document record")
    public ApiResponse<DocumentResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(documentService.getOne(id));
    }

    /**
     * A link, not the file.
     *
     * Returned as JSON with an expiry so the frontend can tell "this link went
     * stale, ask for another" apart from "this file is gone". Streaming the
     * bytes through this service instead would work locally and fall apart in
     * production, where the whole point is that S3 serves the file and we never
     * touch it.
     */
    @GetMapping("/{id}/url")
    @Operation(summary = "Short-lived link to the file. Never a permanent URL.")
    public ApiResponse<PresignedUrlResponse> downloadUrl(@PathVariable @Positive Long id) {
        return ApiResponse.ok(documentService.downloadUrl(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Everything this person has uploaded, newest first")
    public ApiResponse<List<DocumentResponse>> listForUser(
            @PathVariable @Positive Long userId,
            @RequestParam(required = false) DocumentType docType) {

        return ApiResponse.ok(docType == null
                ? documentService.listForUser(userId)
                : documentService.listForUserByType(userId, docType));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's own documents")
    public ApiResponse<List<DocumentResponse>> mine() {
        return ApiResponse.ok(documentService.listForUser(currentUser.userId()));
    }

    @GetMapping("/user/{userId}/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "What on this person still needs checking")
    public ApiResponse<List<DocumentResponse>> listPending(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(documentService.listPendingForUser(userId));
    }

    /**
     * PATCH, not PUT: this changes the verification decision and nothing else
     * about the document. A PUT would imply the whole record is being replaced,
     * and the storage key and file name are not the admin's to rewrite.
     */
    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Verify a document, or reject it with remarks")
    public ApiResponse<DocumentResponse> decide(
            @PathVariable @Positive Long id,
            @Valid @RequestBody DocumentVerificationDto dto) {

        DocumentResponse decided = documentService.decide(id, dto);
        return ApiResponse.ok(
                decided.verified() ? "Document verified" : "Document rejected", decided);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Delete an unverified document and its file. A verified one cannot be deleted.")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        documentService.delete(id);
        return ApiResponse.ok("Document removed", null);
    }
}
