package com.perimity.gatepass.messaging.contract;

/**
 * qr-service reports back. Also part of the contract with Sanjay.
 *
 * A failure is reported explicitly rather than by silence. Without this, a pass
 * whose generation failed would sit at PENDING forever with nobody knowing
 * why - and the holder would simply never receive an email.
 *
 * Day 10: batchId added. Sanjay's publisher already sends it; this record was
 * dropping it on the floor, which is why the batch progress counter never
 * moved. Jackson ignores unknown properties by default in Spring Boot, so the
 * mismatch never threw - it just quietly did not work.
 */
public record QrGenerationResult(
        String jobId,
        Long passId,

        /*
         * Null for a single approval, set for a bulk row. QrResultListener
         * uses it to bump the batch counter, and does nothing when it is null.
         */
        Long batchId,

        boolean success,
        String qrKey,
        String pdfKey,
        String failureReason,
        int attempt
) {

    public static QrGenerationResult ok(String jobId, Long passId, Long batchId,
                                        String qrKey, String pdfKey) {
        return new QrGenerationResult(jobId, passId, batchId, true, qrKey, pdfKey, null, 1);
    }

    public static QrGenerationResult failed(String jobId, Long passId, Long batchId,
                                            String reason, int attempt) {
        return new QrGenerationResult(jobId, passId, batchId, false, null, null, reason, attempt);
    }
}
