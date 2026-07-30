package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.DocumentCreateDto;
import com.perimity.user.dto.request.DocumentVerificationDto;
import com.perimity.user.dto.response.DocumentResponse;
import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Documents attached to a person.
 *
 * These endpoints move METADATA only - a storage key, a file name, a declared
 * mime type, and a verification decision. No endpoint here accepts or returns
 * file bytes, and none returns a public URL to one either: a permanent link to
 * somebody's id proof is readable by anyone who ever sees the JSON. The
 * short-lived pre-signed URL for actually displaying a file is a separate
 * endpoint and arrives on Day 9 with the upload path.
 *
 * Verification is administrative. A Faculty member is staff for the purpose of
 * reading a profile, but approving an identity document is a Campus Admin or
 * Super Admin act, which is why the two @PreAuthorize lists below differ.
 */
@RestController
@RequestMapping("/api/user/documents")
@Validated
@Tag(name = "Documents", description = "Storage keys and verification state. Never file bytes.")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Register a file that is already in object storage")
    public ResponseEntity<ApiResponse<DocumentResponse>> register(
            @Valid @RequestBody DocumentCreateDto dto) {

        DocumentResponse created = documentService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Document registered and awaiting verification", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one document record")
    public ApiResponse<DocumentResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(documentService.getOne(id));
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
    @Operation(summary = "Delete an unverified document record. A verified one cannot be deleted.")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        documentService.delete(id);
        return ApiResponse.ok("Document removed", null);
    }
}
