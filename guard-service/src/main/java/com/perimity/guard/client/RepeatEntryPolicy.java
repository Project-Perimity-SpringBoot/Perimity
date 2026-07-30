package com.perimity.guard.client;

/**
 * What a campus wants shown when a pass is scanned a second time in one day.
 *
 * FR-SCAN-8. Campus config key `repeat_entry_result`, documented default AMBER.
 *
 * Note what is NOT here: a REFUSE value. A repeat entry is never a refusal - the
 * paper register this replaces had multiple lines for the same person on the
 * same day, and so does this one. The only question is whether the guard is told
 * about it.
 */
public enum RepeatEntryPolicy {

    /** Say nothing. A repeat looks exactly like a first entry. */
    GREEN,

    /** Show amber so the guard knows, and let them through anyway. */
    AMBER;

    /** The documented default when a campus has not set the key (FR-CFG-3). */
    public static final RepeatEntryPolicy DEFAULT = AMBER;

    public static RepeatEntryPolicy parse(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEFAULT;
        }
    }
}
