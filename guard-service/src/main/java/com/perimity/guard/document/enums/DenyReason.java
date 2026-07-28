package com.perimity.guard.document.enums;

/**
 * Why a scan was refused. Recorded on every denied attempt so the audit trail
 * shows refusals, not only successful entries - a paper register never could.
 */
public enum DenyReason {
    EXPIRED,
    REVOKED,
    PAUSED,
    PENDING,
    INVALID_TOKEN,
    WRONG_CAMPUS;

    /** Guard-facing text. Never leak internal detail to the person at the gate. */
    public String guardMessage() {
        return switch (this) {
            case EXPIRED -> "Pass expired";
            case REVOKED -> "Pass revoked";
            case PAUSED -> "Pass paused - awaiting re-approval";
            case PENDING -> "Pass not yet active";
            case INVALID_TOKEN -> "Invalid pass";
            case WRONG_CAMPUS -> "Pass not valid at this campus";
        };
    }
}
