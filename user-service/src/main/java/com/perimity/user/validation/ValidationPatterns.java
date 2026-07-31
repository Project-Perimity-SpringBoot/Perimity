package com.perimity.user.validation;

/**
 * Every regular expression used by user-service, in one place.
 *
 * Two rules govern what may go in here:
 *
 * 1. Campus-agnostic. No pattern may assume a country, an email domain or an
 *    alphabet. A roll-number format that hard-codes one institution's scheme
 *    would break the moment a second campus onboards.
 *
 * 2. Regex validates SHAPE, never MEANING. "Does this department exist on this
 *    campus", "is this roll number already taken" are database questions and
 *    belong in the service layer.
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
     * Roll number / employee id. Campus-agnostic: letters, digits, hyphen and
     * slash, because institutions format these wildly differently. Deliberately
     * permissive on content, strict only on length and allowed characters.
     */
    public static final String IDENTIFIER_CODE = "^[A-Za-z0-9][A-Za-z0-9/-]{0,31}$";
    public static final String IDENTIFIER_CODE_MESSAGE =
            "May contain letters, digits, hyphens and slashes only";

    /**
     * A department code, e.g. a short token each campus defines for itself.
     * Never a hard-coded list - departments are per-campus seeded data.
     */
    public static final String DEPARTMENT_CODE = "^[A-Za-z0-9][A-Za-z0-9 ._-]{0,31}$";
    public static final String DEPARTMENT_CODE_MESSAGE =
            "Department code may contain letters, digits, spaces, dots, hyphens and underscores";

    /**
     * An S3 object key. Blocks path traversal ("..") and a leading slash, both
     * of which are how a crafted key escapes the intended prefix. This is the
     * one pattern here that is a genuine security control, not a shape check.
     */
    public static final String OBJECT_KEY = "^(?!.*\\.\\.)(?!/)[A-Za-z0-9!_.*'()/-]{1,512}$";
    public static final String OBJECT_KEY_MESSAGE =
            "Invalid storage key: no leading slash and no parent-directory segments";

    /** A generic short title / label. */
    public static final String TITLE = "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,'&()/-]{0,149}$";
    public static final String TITLE_MESSAGE =
            "Title contains characters that are not allowed";
}
