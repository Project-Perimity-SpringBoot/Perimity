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

    /**
     * The AES-256 token as it travels: URL-safe Base64, so it survives being
     * put in a QR code and then posted back as JSON by guard-service.
     *
     * The lower bound of 24 matters. AES-256-GCM output for even a tiny
     * payload is well over 24 Base64 characters, so anything shorter cannot
     * be one of our tokens - it is a truncated scan or a hand-typed guess,
     * and rejecting it here costs nothing instead of a pointless decrypt.
     */
    // The lookahead bounds the TOTAL length including any "=" padding. Written
    // as [A-Za-z0-9_-]{24,512}={0,2} the bound would cover only the body, so a
    // padded token could reach 514 and disagree with the @Size(max = 512) that
    // guards it.
    public static final String QR_TOKEN = "^(?=.{1,512}$)[A-Za-z0-9_-]+={0,2}$";
    public static final String QR_TOKEN_MESSAGE =
            "Token must be URL-safe Base64 or valid pass code, 1 to 512 characters";

    /**
     * Unicode-aware: Devanagari, Arabic and accented Latin names all pass.
     *
     * LITERAL SPACE, not \s - inside a character class \s also matches \n, \r
     * and \t. A name reaching this service is rendered into a QR payload and
     * written to logs, and a name carrying a newline splits one log field into
     * two lines with the second under the writer's control.
     *
     * Kept identical to auth-service's copy on purpose. These patterns are a
     * cross-service contract: a name auth-service accepts must not be rejected
     * here, or a valid account becomes unable to hold a pass.
     */
    public static final String PERSON_NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]{1,119}$";
    public static final String PERSON_NAME_MESSAGE =
            "Name may contain letters, spaces, apostrophes, hyphens and full stops only";
}
