package com.perimity.gatepass.entity.enums;

/**
 * Approval state of a visitor request (FR-APPR-1 ... FR-APPR-6).
 * A request becomes a gate pass only after it reaches APPROVED.
 */
public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
