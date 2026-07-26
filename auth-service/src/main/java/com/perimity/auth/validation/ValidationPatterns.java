package com.perimity.auth.validation;

/**
 * Every regular expression used by auth-service, in one place.
 *
 * Two rules govern what may go in here:
 *
 * 1. Campus-agnostic. No pattern may assume a country, an email domain or an
 *    alphabet. An email regex that requires one institution's domain would
 *    lock out every visitor and break the whole product.
 *
 * 2. Regex validates SHAPE, never MEANING. "Is this email on the blocklist",
 *    "is this account locked", "has this OTP already been consumed" are all
 *    database questions and belong in the service layer.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Stricter than the built-in @Email, which accepts nonsense like "a@b". */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$";
    public static final String EMAIL_MESSAGE = "Enter a valid email address";

    /** E.164 shape. Normalise (strip spaces, dashes, brackets) before validating. */
    public static final String PHONE = "^\\+?[1-9]\\d{6,14}$";
    public static final String PHONE_MESSAGE =
            "Enter a valid phone number in international format, digits only, optional leading +";

    /** Unicode-aware: Devanagari, Arabic and accented Latin names all pass. */
    public static final String PERSON_NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{1,119}$";
    public static final String PERSON_NAME_MESSAGE =
            "Name may contain letters, spaces, apostrophes, hyphens and full stops only";

    /**
     * A bcrypt hash. This is a tripwire, not a formality: if a plain-text
     * password is ever assigned to password_hash, validation fails loudly
     * instead of silently storing it. Worth more than any comment.
     */
    public static final String BCRYPT_HASH = "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";
    public static final String BCRYPT_HASH_MESSAGE =
            "Password must be stored as a bcrypt hash, never in plain text";

    /** A SHA-256 hex digest: exactly 64 hex characters. Same tripwire for OTPs. */
    public static final String SHA256_HEX = "^[a-fA-F0-9]{64}$";
    public static final String SHA256_HEX_MESSAGE =
            "Value must be a 64-character SHA-256 hex digest, never plain text";

    /**
     * PASSWORD POLICY - for the incoming DTO on Day 3, never on the entity.
     * By the time a value reaches the entity it is a hash, and a hash can
     * never satisfy this. At least 8 characters, one upper, one lower, one digit.
     * PASSWORD_MIN_LENGTH in .env is the configurable version; keep them in step.
     */
    public static final String PASSWORD_POLICY =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_POLICY_MESSAGE =
            "Password must be at least 8 characters and include an uppercase letter, "
            + "a lowercase letter and a digit";

    /** IPv4 or IPv6, for the audit log's source_ip column. */
    public static final String IP_ADDRESS =
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}"
            + "|[0-9A-Fa-f:]{2,45})$";
    public static final String IP_ADDRESS_MESSAGE = "Invalid IP address";
}
