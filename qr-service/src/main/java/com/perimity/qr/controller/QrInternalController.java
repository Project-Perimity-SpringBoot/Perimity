package com.perimity.qr.controller;

import com.perimity.qr.dto.ApiResponse;
import com.perimity.qr.dto.QrDecryptRequest;
import com.perimity.qr.dto.UndeliveredEmailResponse;
import java.util.List;
import com.perimity.qr.dto.QrDecryptResponse;
import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.ResendEmailRequest;
import com.perimity.qr.email.PassEmailRetryService;
import com.perimity.qr.entity.enums.EmailStatus;
import java.util.Map;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.service.QrDecryptService;
import com.perimity.qr.service.QrRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final QrDecryptService qrDecryptService;

    public QrInternalController(QrRecordService qrRecordService,
                                PassEmailRetryService passEmailRetryService,
                                QrDecryptService qrDecryptService) {
        this.qrRecordService = qrRecordService;
        this.passEmailRetryService = passEmailRetryService;
        this.qrDecryptService = qrDecryptService;
    }

    /**
     * DAY 11. The scan path. guard-service posts whatever came out of the
     * camera; this answers whether it is a genuine, still-current token.
     *
     * POST rather than GET, and the token in the body rather than the path,
     * for one reason: a URL ends up in access logs, browser history, proxy
     * caches and error reports. A pass token in any of those is a pass anyone
     * who reads them can use. A request body appears in none of them.
     *
     * The response deliberately carries no verdict, no holder name and no
     * photo - see QrDecryptResponse. Whether this pass may enter this gate
     * today is guard-service's call, and the holder's details are
     * user-service's data.
     *
     * @Valid is what makes the 512-character ceiling and the URL-safe Base64
     * pattern on QrDecryptRequest actually run. Without it every constraint is
     * skipped and this endpoint would hand arbitrary user input straight to a
     * cipher - which is the one place in this service where that matters most.
     */
    @PostMapping("/decrypt")
    @Operation(summary = "Decrypt a scanned token and report whether it is still the live one")
    public ApiResponse<QrDecryptResponse> decrypt(@Valid @RequestBody QrDecryptRequest request) {
        QrDecryptResponse result = qrDecryptService.decrypt(request);

        /*
         * Always HTTP 200, even when the token is refused.
         *
         * A refused token is a successful answer to the question asked, not a
         * failed request. Returning 4xx would make guard-service's `call()`
         * wrapper treat every forged scan as an outage - and its comment says
         * exactly what that costs: "a timeout means we do not know whether the
         * pass is valid", which must never be turned into a red card.
         *
         * The verdict lives in the body, where the caller reads it deliberately.
         */
        return ApiResponse.ok(
                result.isTokenValid() ? "Token is valid" : "Token refused",
                result);
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

    /**
     * DAY 10. Passes whose holder was never told.
     *
     * The repair hook for a mail server that was down during a bulk upload:
     * the passes are fine, the QRs are in storage, and several hundred people
     * simply have not heard. Without this the only record is a log line nobody
     * greps.
     *
     * Returns pass ids, NOT email addresses - see UndeliveredEmailResponse.
     * gatepass-service holds the addresses and drives the resends through
     * POST /{passId}/resend-email.
     */
    @GetMapping("/emails/undelivered")
    @Operation(summary = "Passes whose email failed or never went out. No addresses returned.")
    public ApiResponse<List<UndeliveredEmailResponse>> undeliveredEmails() {
        List<UndeliveredEmailResponse> undelivered = passEmailRetryService.undelivered();
        return ApiResponse.ok(undelivered.size() + " pass email(s) undelivered", undelivered);
    }
}
