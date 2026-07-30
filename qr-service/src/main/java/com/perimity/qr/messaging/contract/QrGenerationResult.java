package com.perimity.qr.messaging.contract;

/**
 * ==========================================================================
 *  SHARED CONTRACT - owned by gatepass-service (Tushar).
 *
 *  Source of truth:
 *    gatepass-service/src/main/java/com/perimity/gatepass/messaging/contract/
 *        QrGenerationResult.java
 *
 *  This is a copy. Change only the package line. Any field change is agreed
 *  with Tushar first and applied to BOTH copies in the same commit.
 * ==========================================================================
 *
 * What qr-service publishes back on qr.generate.result when a job settles.
 *
 * A failure is reported explicitly rather than by silence. Without this
 * message a pass whose generation failed would sit at PENDING forever with
 * nobody knowing why, and the holder would simply never receive an email.
 *
 * Reporting by message rather than by an HTTP call back into gatepass-service
 * is the part that matters. If gatepass is restarting at the moment generation
 * finishes, an HTTP callback fails, retries, and eventually marks the job
 * FAILED - even though the QR and the PDF were produced correctly and are
 * sitting in storage. A message just waits in the queue until gatepass is
 * back. This is Tushar's design decision and it is the right one.
 *
 * ONE ADDITION TO TUSHAR'S VERSION: batchId, for the same reason as on
 * QrGenerationJob - the Bulk Progress screen needs to attribute a result to a
 * batch. Reads as null until he adds it.
 */
public record QrGenerationResult(
        String jobId,
        Long passId,
        Long batchId,
        boolean success,
        String qrKey,
        String pdfKey,
        String failureReason,
        int attempt
) {

    /**
     * Note the attempt parameter, which Tushar's ok() hardcodes to 1.
     *
     * A pass that succeeded on its third try is a different operational fact
     * from one that succeeded immediately - it means something upstream was
     * flapping. Hardcoding 1 discards exactly the signal the field exists to
     * carry, and the loss is invisible because the value still looks plausible.
     */
    public static QrGenerationResult ok(String jobId, Long passId, Long batchId,
                                        String qrKey, String pdfKey, int attempt) {
        return new QrGenerationResult(jobId, passId, batchId, true,
                qrKey, pdfKey, null, attempt);
    }

    public static QrGenerationResult failed(String jobId, Long passId, Long batchId,
                                            String reason, int attempt) {
        return new QrGenerationResult(jobId, passId, batchId, false,
                null, null, reason, attempt);
    }
}
