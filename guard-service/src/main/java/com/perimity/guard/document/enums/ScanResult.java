package com.perimity.guard.document.enums;

/**
 * What the scanner shows the guard.
 *
 * GREEN - allow entry.
 * RED   - deny; denyReason says why.
 * AMBER - allowed but flagged. Driven by the campus's repeat_entry_result
 *         config key from campus-service, not hardcoded here.
 */
public enum ScanResult {
    GREEN,
    RED,
    AMBER;

    public boolean isEntryAllowed() {
        return this != RED;
    }
}
