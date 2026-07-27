package com.perimity.qr.validation;

/**
 * Every regular expression used by qr-service, in one place.
 *
 * Campus-agnostic: no pattern may assume a country, an email domain or an
 * alphabet. Regex validates SHAPE only - whether a pass is still active or a
 * token has been invalidated is a database question for the service layer.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** S3-safe object key. Rejects path traversal via "..". */
    public static final String OBJECT_KEY = "^(?!.*\\.\\.)[A-Za-z0-9][A-Za-z0-9!_.*'()/-]{0,299}$";
    public static final String OBJECT_KEY_MESSAGE = "Invalid object storage key";

    /**
     * A SHA-256 hex digest: exactly 64 hex characters.
     * If a value reaching token_hash is not this shape, something wrote a plain
     * token into the column - which must never happen.
     */
    public static final String SHA256_HEX = "^[a-fA-F0-9]{64}$";
    public static final String SHA256_HEX_MESSAGE =
            "Token hash must be a 64-character SHA-256 hex digest";

    /** Stricter than the built-in @Email, which accepts "a@b". */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$";
    public static final String EMAIL_MESSAGE = "Enter a valid email address";

    /** Unicode-aware: Devanagari, Arabic and accented Latin names all pass. */
    public static final String PERSON_NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{1,119}$";
    public static final String PERSON_NAME_MESSAGE =
            "Name may contain letters, spaces, apostrophes, hyphens and full stops only";
}
