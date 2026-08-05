package com.perimity.user.bulk;

import java.util.List;
import java.util.Locale;

/**
 * The columns a Google Form responses sheet is expected to carry, and the
 * header text each one might actually appear under.
 *
 * ==========================================================================
 * WHY MATCHING IS FUZZY AND NOT POSITIONAL
 * ==========================================================================
 * The header row is whatever somebody typed as the question. It is not a
 * schema: "Date of birth", "date of birth", "DOB", "Date of Birth " with a
 * trailing space are all the same question, and Forms adds its own
 * "Timestamp" and "Email Address" columns wherever it likes.
 *
 * Reading by POSITION would break the first time anyone reorders a question or
 * adds one - which is a thing faculty will do, because it is their form. So
 * columns are found by header text, normalised, against a list of aliases.
 *
 * A column that cannot be found is reported by NAME before anything is parsed,
 * so the failure is "your sheet has no Roll number column" rather than two
 * hundred rows rejected for missing roll numbers.
 */
public enum FormColumn {

    EMAIL(true, "email address", "email", "username"),
    FULL_NAME(true, "full name", "name", "student name"),
    FIRST_NAME(true, "first name", "firstname", "given name"),
    MIDDLE_NAME(false, "middle name", "middlename"),
    LAST_NAME(true, "last name", "lastname", "surname", "family name"),
    DATE_OF_BIRTH(true, "date of birth", "dob", "birth date", "birthdate"),
    GENDER(true, "gender", "sex"),
    ADDRESS(true, "address", "residential address", "home address"),
    PHONE_COUNTRY_CODE(false, "phone country code", "country code", "code"),
    PHONE_NUMBER(true, "phone number", "phone", "mobile", "mobile number", "contact number"),
    ROLL_NO(true, "roll number", "roll no", "rollno", "roll", "enrollment number"),
    DEPARTMENT(true, "department", "branch", "course", "programme", "program"),
    PHOTO(true, "passport photo", "photo", "photograph", "passport size photo",
            "upload your passport size photo");

    private final boolean required;
    private final List<String> aliases;

    FormColumn(boolean required, String... aliases) {
        this.required = required;
        this.aliases = List.of(aliases);
    }

    /**
     * PHONE_COUNTRY_CODE is optional because most forms will not ask for it -
     * a campus-agnostic product cannot assume +91, but a single-country intake
     * reasonably will. Missing means the campus default is applied, and that
     * default is a configured value, never a literal in this file.
     *
     * MIDDLE_NAME is optional because plenty of people do not have one.
     *
     * Everything else is required, including the photo: a student with no photo
     * cannot hold a pass, so importing them without one produces a person who
     * cannot get through the gate.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Does this header text mean this column?
     *
     * Google appends its own suffixes to file-upload questions, so the match is
     * "starts with" rather than equality - "Passport photo (File responses)"
     * has to resolve to PHOTO. Exact equality would silently miss it and report
     * a missing photo column on a sheet that has one.
     */
    public boolean matches(String header) {
        String normalised = normalise(header);
        if (normalised.isEmpty()) {
            return false;
        }
        return aliases.stream().anyMatch(
                alias -> normalised.equals(alias) || normalised.startsWith(alias));
    }

    /**
     * Lower-cased, trimmed, punctuation and runs of whitespace flattened.
     *
     * A header pasted from a document often carries a non-breaking space, which
     * looks identical on screen and is not the space character. Left in, it
     * makes "Roll number" not equal "Roll number" and produces a bug report
     * nobody can reproduce by typing.
     */
    public static String normalise(String header) {
        if (header == null) {
            return "";
        }
        return header
                .replace(' ', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[*?:.]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
