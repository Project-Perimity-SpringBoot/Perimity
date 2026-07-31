package com.perimity.guard.document.enums;

/**
 * Outcome of a gate scan.
 *
 * Persisted as a string so adding new values later cannot shift the meaning of
 * existing rows.
 *
 * ==========================================================================
 * AMBER IS AN ENTRY, NOT A REFUSAL
 * ==========================================================================
 * The person goes in. Amber only tells the guard "this pass has already been
 * scanned today" - it is information, not an instruction to stop. Anything that
 * buckets results into allowed-or-denied must group AMBER with ALLOWED, or the
 * shift counters, the attendance figures and the security report all quietly
 * disagree with the register.
 *
 * Whether a repeat entry shows green or amber is a per-campus decision, read
 * from the campus config key `repeat_entry_result` (FR-SCAN-8). It is not a rule
 * this service is allowed to invent.
 */
public enum ScanResult {

    /** Green. Enter. */
    ALLOWED,

    /** Amber. Enter, but this pass has already been scanned today. */
    AMBER,

    /** Red. Turn them away - denialReason says why. */
    DENIED;

    /** True when the person may walk through. Amber counts. */
    public boolean permitsEntry() {
        return this != DENIED;
    }
}
