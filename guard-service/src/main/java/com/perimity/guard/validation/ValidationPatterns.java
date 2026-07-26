package com.perimity.guard.validation;

/**
 * Every regular expression used by guard-service, in one place.
 *
 * Campus-agnostic: no pattern assumes a country, an email domain or an
 * alphabet. Regex validates SHAPE only - whether a pass is active, revoked or
 * belongs to this campus is answered by gatepass-service, not by a pattern.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Unicode-aware: Devanagari, Arabic and accented Latin names all pass. */
    public static final String PERSON_NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{1,119}$";
    public static final String PERSON_NAME_MESSAGE =
            "Name may contain letters, spaces, apostrophes, hyphens and full stops only";

    /**
     * Free-text device label sent by the scanner, e.g. "Android 14 / Chrome".
     * Deliberately permissive but bounded - it is written to a log, so it must
     * not carry control characters or newlines that could forge a log line.
     */
    public static final String DEVICE_LABEL = "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s._/()+-]{0,119}$";
    public static final String DEVICE_LABEL_MESSAGE = "Invalid device label";

    /** Stricter than the built-in @Email, which accepts "a@b". */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$";
    public static final String EMAIL_MESSAGE = "Enter a valid email address";
}
