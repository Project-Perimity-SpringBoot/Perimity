package com.perimity.guard.document.enums;

/**
 * Outcome of a gate scan.
 * Persisted as a string (EnumType.STRING) so adding new values later
 * cannot shift the meaning of existing rows.
 */
public enum ScanResult {
    ALLOWED,
    DENIED
}