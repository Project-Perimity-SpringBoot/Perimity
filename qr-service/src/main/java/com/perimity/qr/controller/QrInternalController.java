package com.perimity.qr.controller;

import com.perimity.qr.dto.ApiResponse;
import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.service.QrRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints. No browser ever calls these.
 *
 * Kept in a separate controller from QrController rather than as one more
 * method on it, for one reason: on Day 7 the whole of /api/internal/** gets
 * locked to internal callers in one rule. Mixed in with the public read
 * endpoints, that lock would have to be written per method, and the method
 * someone adds next week would be the one that gets missed.
 *
 * @Validated at class level is what makes the @Positive on the path variable
 * below actually run. It does NOT cover the request body - that needs @Valid
 * on the parameter itself, which is the single most common silent failure in
 * a Spring controller: without it, every constraint on the DTO is skipped and
 * the endpoint quietly accepts anything.
 */
@RestController
@RequestMapping("/api/internal/qr")
@Validated
@Tag(name = "QR Internal", description = "Service-to-service QR operations")
public class QrInternalController {

    private final QrRecordService qrRecordService;

    public QrInternalController(QrRecordService qrRecordService) {
        this.qrRecordService = qrRecordService;
    }

    /**
     * Called by gatepass-service when a pass is re-issued or revoked.
     *
     * Returns the record rather than a bare 200 so the caller can log exactly
     * which QR it retired - on a re-issue there will be a new one moments
     * later, and "which row did that call actually touch" is unanswerable
     * afterwards otherwise.
     */
    @PostMapping("/invalidate/{passId}")
    @Operation(summary = "Invalidate the active QR for a pass, on re-issue or revoke")
    public ApiResponse<QrRecordResponse> invalidate(
            @PathVariable @Positive(message = "passId must be a positive id") Long passId,
            @Valid @RequestBody QrInvalidateRequest request) {

        return ApiResponse.ok("QR invalidated", qrRecordService.invalidate(passId, request));
    }
}
