package com.perimity.gatepass.entity.enums;

/**
 * Two-phase bulk upload (FR-BULK-1 ... FR-BULK-10).
 *
 * VALIDATING - fast synchronous pass: parse the sheet, check every row.
 * VALIDATED  - summary shown ("580 valid, 20 errors"); waiting for Confirm.
 * PROCESSING - confirmed; identities created and QR/PDF jobs queued to RabbitMQ.
 * COMPLETED  - every valid row has been handed off.
 * FAILED     - the file itself was unreadable or over the row limit.
 */
public enum BatchStatus {
    VALIDATING,
    VALIDATED,
    PROCESSING,
    COMPLETED,
    FAILED
}
