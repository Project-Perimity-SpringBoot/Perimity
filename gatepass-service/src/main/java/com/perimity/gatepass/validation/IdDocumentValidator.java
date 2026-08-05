package com.perimity.gatepass.validation;

import com.perimity.gatepass.entity.enums.IdType;

/**
 * Per-type rules for an Indian identity document number.
 *
 * Kept as a plain class rather than field regexes because the rule depends on
 * ANOTHER field: the same string is valid as a PAN and nonsense as a passport.
 * A field-level @Pattern cannot express that, so the DTO carries a class-level
 * @ValidIdDocument and this holds the logic - callable from tests and from the
 * frontend's mirror without instantiating a validator.
 *
 * Formats:
 *   AADHAAR   12 digits, not all the same digit, Verhoeff checksum
 *   PAN       5 uppercase letters + 4 digits + 1 uppercase letter
 *   PASSPORT  1 uppercase letter + 7 digits
 *   VOTER_ID  3 uppercase letters + 7 digits
 */
public final class IdDocumentValidator {

    private IdDocumentValidator() {
    }

    public static final String AADHAAR = "^[0-9]{12}$";
    public static final String PAN = "^[A-Z]{5}[0-9]{4}[A-Z]$";
    public static final String PASSPORT = "^[A-Z][0-9]{7}$";
    public static final String VOTER_ID = "^[A-Z]{3}[0-9]{7}$";

    /**
     * Verhoeff multiplication table (D5 dihedral group). UIDAI uses Verhoeff
     * rather than a simple modulus because it catches every single-digit error
     * AND every adjacent transposition - the two mistakes a human actually
     * makes copying a number off a card.
     */
    private static final int[][] D = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
        {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
        {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
        {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
        {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
        {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
        {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
        {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
        {9, 8, 7, 6, 5, 4, 3, 2, 1, 0},
    };

    /** Permutation table, applied by position. */
    private static final int[][] P = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8},
    };

    /**
     * True when the number satisfies its type's format.
     *
     * A null or blank number is TRUE here: the field is optional, and "did you
     * supply one" is a different question from "is the one you supplied valid".
     * The DTO's own pairing rule decides whether it was required.
     */
    public static boolean isValid(IdType type, String number) {
        if (number == null || number.isBlank() || type == null) {
            return true;
        }

        String value = normalise(number);

        return switch (type) {
            case AADHAAR -> value.matches(AADHAAR) && notAllSameDigit(value) && verhoeff(value);
            case PAN -> value.matches(PAN);
            case PASSPORT -> value.matches(PASSPORT);
            case VOTER_ID -> value.matches(VOTER_ID);
        };
    }

    /**
     * Spaces and hyphens are how people read a number off the card - Aadhaar is
     * printed in three groups of four, and a PAN is often written with a dash.
     * Rejecting the separators would fail the number the visitor is looking
     * straight at, so they are stripped rather than refused.
     */
    public static String normalise(String number) {
        return number == null ? null : number.replaceAll("[\\s-]", "").trim().toUpperCase();
    }

    /** 111111111111 has a valid shape and is not a real Aadhaar. */
    private static boolean notAllSameDigit(String value) {
        return value.chars().distinct().count() > 1;
    }

    /**
     * Verhoeff check. The number is valid when the running product over its
     * digits, read RIGHT to left, comes back to 0.
     */
    public static boolean verhoeff(String digits) {
        int c = 0;
        int[] reversed = new int[digits.length()];
        for (int i = 0; i < digits.length(); i++) {
            reversed[i] = Character.getNumericValue(digits.charAt(digits.length() - 1 - i));
        }
        for (int i = 0; i < reversed.length; i++) {
            c = D[c][P[i % 8][reversed[i]]];
        }
        return c == 0;
    }

    /**
     * The only part of a document number worth keeping.
     *
     * A gate asks "did a guard check a document, and did it match". The last
     * four digits plus the type answers that: the visitor reads them off the
     * card in front of them, and a leak of four digits is not a leak of an
     * identity. The full number is validated first - checksum included - and
     * then discarded, so the number was proven real without being retained.
     *
     * Aadhaar is the reason this exists. Storing one in full puts regulated
     * personal data in a campus gate log, which has no lawful basis to hold it.
     * The same rule is applied to all four types because none of them need to
     * be whole either.
     */
    public static String lastFour(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String value = normalise(number);
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    /** What to tell someone who got it wrong. Names the shape, not the rule. */
    public static String messageFor(IdType type) {
        return switch (type) {
            case AADHAAR -> "Check the number - 12 digits, and one of them looks wrong";
            case PAN -> "A PAN is 5 letters, 4 digits and a letter, e.g. ABCDE1234F";
            case PASSPORT -> "A passport number is a letter followed by 7 digits, e.g. A1234567";
            case VOTER_ID -> "A voter ID is 3 letters followed by 7 digits, e.g. ABC1234567";
        };
    }
}
