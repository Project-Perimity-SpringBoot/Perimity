package com.perimity.qr.controller;

import com.perimity.qr.dto.ApiResponse;
import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.ResendEmailRequest;
import com.perimity.qr.email.PassEmailRetryService;
import com.perimity.qr.entity.enums.EmailStatus;
import java.util.Map;
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
    private final PassEmailRetryService passEmailRetryService;

    public QrInternalController(QrRecordService qrRecordService,
                                PassEmailRetryService passEmailRetryService) {
        this.qrRecordService = qrRecordService;
        this.passEmailRetryService = passEmailRetryService;
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

    /**
     * DAY 9. Resends the pass email without regenerating the QR.
     *
     * The repair for a mail server that was down while passes were being
     * issued. Regenerating instead would mint a new token and invalidate the QR
     * the holder may already be carrying - the wrong fix for a problem that was
     * never in the pass.
     *
     * Internal, like everything else in this controller. A holder who could
     * call it would be able to post a pass PDF to any address they chose.
     */
    @PostMapping("/{passId}/resend-email")
    @Operation(summary = "Resend the pass email for an existing pass. Does not regenerate.")
    public ApiResponse<Map<String, String>> resendEmail(
            @PathVariable @Positive(message = "passId must be a positive id") Long passId,
            @Valid @RequestBody ResendEmailRequest request) {

        EmailStatus status = passEmailRetryService.resend(
                passId, request.getEmail(), request.getSubject(), request.getBody());

        return ApiResponse.ok("Resend attempted", Map.of("emailStatus", status.name()));
    }
}
