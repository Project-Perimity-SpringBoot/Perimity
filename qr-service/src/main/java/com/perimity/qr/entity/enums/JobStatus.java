package com.perimity.qr.entity.enums;

/**
 * Lifecycle of one QR/PDF generation job consumed from the qr.generate.request queue.
 *
 * QUEUED     - message received, nothing done yet.
 * PROCESSING - token, QR PNG and PDF are being produced.
 * DONE       - both files uploaded and gatepass-service was told to activate.
 * FAILED     - retries exhausted; the message went to qr.generate.dlq.
 */
public enum JobStatus {
    QUEUED,
    PROCESSING,
    DONE,
    FAILED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED;
    }
}
