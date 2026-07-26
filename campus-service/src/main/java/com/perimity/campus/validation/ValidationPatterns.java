package com.perimity.campus.validation;

/**
 * Every regular expression used by campus-service, in one place.
 *
 * Two rules govern what may go in here:
 *
 * 1. Campus-agnostic. No pattern may bake in one institution's name, code
 *    scheme or country. This service defines campuses; hard-coding one would
 *    defeat the entire multi-tenant purpose of the product.
 *
 * 2. Regex validates SHAPE, never MEANING. "Is this campus code already taken",
 *    "does this gate belong to this campus" are database questions and belong
 *    in the service layer.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * A campus code: a short, url-safe token the platform admin assigns, e.g.
     * a slug. Letters, digits and hyphens only; this often ends up in an S3
     * prefix and a URL, so keep it tight. NOT a hard-coded institution list.
     */
    public static final String CAMPUS_CODE = "^[A-Za-z0-9][A-Za-z0-9-]{1,31}$";
    public static final String CAMPUS_CODE_MESSAGE =
            "Campus code may contain letters, digits and hyphens only, 2 to 32 characters";

    /** A human-facing name for a campus or gate. Unicode-aware. */
    public static final String DISPLAY_NAME = "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,'&()/-]{1,149}$";
    public static final String DISPLAY_NAME_MESSAGE =
            "Name contains characters that are not allowed";

    /** Stricter than the built-in @Email, which accepts nonsense like "a@b". */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$";
    public static final String EMAIL_MESSAGE = "Enter a valid email address";

    /** E.164 shape. Normalise (strip spaces, dashes, brackets) before validating. */
    public static final String PHONE = "^\\+?[1-9]\\d{6,14}$";
    public static final String PHONE_MESSAGE =
            "Enter a valid phone number in international format, digits only, optional leading +";

    /**
     * A config key in campus_config. Dotted lower-case tokens, e.g.
     * "approval.required" or "reentry.allowed". Keeps the key-value store tidy
     * and predictable so every service reads the same key names.
     */
    public static final String CONFIG_KEY = "^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$";
    public static final String CONFIG_KEY_MESSAGE =
            "Config key must be lower-case dotted tokens, e.g. approval.required";

    /**
     * An S3 object key for a campus logo. Blocks path traversal ("..") and a
     * leading slash. This is a genuine security control, not a shape check.
     */
    public static final String OBJECT_KEY = "^(?!.*\\.\\.)(?!/)[A-Za-z0-9!_.*'()/-]{1,512}$";
    public static final String OBJECT_KEY_MESSAGE =
            "Invalid storage key: no leading slash and no parent-directory segments";
}
