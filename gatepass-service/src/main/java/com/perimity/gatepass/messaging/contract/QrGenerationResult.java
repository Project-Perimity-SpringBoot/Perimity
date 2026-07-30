package com.perimity.gatepass.messaging.contract;

/**
 * qr-service reports back. Also part of the contract with Sanjay.
 *
 * A failure is reported explicitly rather than by silence. Without this, a pass
 * whose generation failed would sit at PENDING forever with nobody knowing
 * why - and the holder would simply never receive an email.
 */
public record QrGenerationResult(
        String jobId,
        Long passId,
        boolean success,
        String qrKey,
        String pdfKey,
        String failureReason,
        int attempt
) {

    public static QrGenerationResult ok(String jobId, Long passId, String qrKey, String pdfKey) {
        return new QrGenerationResult(jobId, passId, true, qrKey, pdfKey, null, 1);
    }

    public static QrGenerationResult failed(String jobId, Long passId, String reason, int attempt) {
        return new QrGenerationResult(jobId, passId, false, null, null, reason, attempt);
    }
}
