package com.perimity.gatepass.validation;

/**
 * Every regular expression used by gatepass-service, in one place.
 *
 * Two rules govern what may go in here:
 *
 * 1. Campus-agnostic. No pattern may assume a country, a phone format, an email
 *    domain, or an alphabet. Perimity is deployed by any campus anywhere, so
 *    name patterns use Unicode letter classes and the phone pattern is E.164,
 *    not a ten-digit national format.
 *
 * 2. Regex validates SHAPE, never MEANING. "Does this look like an email" is a
 *    regex question. "Does this email belong to a blocklisted person", "is this
 *    event still running", "has this person already got a pass for this event"
 *    are database questions and belong in the service layer.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    // ---------------------------------------------------------------
    // Email
    // ---------------------------------------------------------------
    /**
     * Stricter than the built-in @Email, which accepts nonsense like "a@b".
     * Requires a dot-separated TLD of at least two letters.
     * Deliberately does NOT restrict the domain - locking it to one institution's
     * domain would break the campus-agnostic rule and shut out every visitor.
     */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$";

    public static final String EMAIL_MESSAGE = "Enter a valid email address";

    // ---------------------------------------------------------------
    // Phone
    // ---------------------------------------------------------------
    /**
     * E.164 shape: optional +, a non-zero leading digit, 7 to 15 digits total.
     * Strip spaces, dashes and brackets before validating - do not try to allow
     * every punctuation style people type, normalise first instead.
     */
    public static final String PHONE = "^\\+?[1-9]\\d{6,14}$";

    public static final String PHONE_MESSAGE =
            "Enter a valid phone number in international format, digits only, optional leading +";

    // ---------------------------------------------------------------
    // Names
    // ---------------------------------------------------------------
    /**
     * A person's name. \p{L} is any Unicode letter and \p{M} any combining mark,
     * so Devanagari, Arabic, Cyrillic and accented Latin names all pass.
     * Blocks digits and symbols, which is what actually catches junk rows in a
     * bulk spreadsheet ("N/A", "-", "12345", "test@test").
     */
    public static final String PERSON_NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{1,119}$";

    public static final String PERSON_NAME_MESSAGE =
            "Name may contain letters, spaces, apostrophes, hyphens and full stops only";

    /** An event or programme title: letters, digits and ordinary punctuation. */
    public static final String TITLE = "^[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N}\\s.,'&()+/-]{2,179}$";

    public static final String TITLE_MESSAGE =
            "Title may contain letters, digits, spaces and basic punctuation only";

    // ---------------------------------------------------------------
    // Object storage
    // ---------------------------------------------------------------
    /** S3-safe key characters. Never allow "..", a leading slash, or a backslash. */
    public static final String OBJECT_KEY = "^(?!.*\\.\\.)[A-Za-z0-9][A-Za-z0-9!_.*'()/-]{0,299}$";

    public static final String OBJECT_KEY_MESSAGE = "Invalid object storage key";

    /**
     * Filename check for a bulk upload. This is a convenience check only.
     * The real check is the file's content type and magic bytes in the service
     * layer - an attacker renames a file in one second.
     */
    public static final String SPREADSHEET_FILENAME =
            "^[^\\\\/:*?\"<>|\\r\\n]{1,255}\\.(?i:xlsx|xls|csv)$";

    public static final String SPREADSHEET_FILENAME_MESSAGE =
            "File must be .xlsx, .xls or .csv";
}
