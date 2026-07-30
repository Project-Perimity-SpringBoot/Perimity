package com.perimity.guard.document.enums;

/**
 * Why a scan was DENIED. Only populated when ScanResult is DENIED.
 */
public enum DenialReason {
    PASS_EXPIRED,
    PASS_REVOKED,
    PASS_PAUSED,
    PASS_PENDING,
    INVALID_TOKEN,
    WRONG_CAMPUS,
    WRONG_GATE,
    OUT_OF_DATE_RANGE
}