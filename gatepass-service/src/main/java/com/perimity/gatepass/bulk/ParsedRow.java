package com.perimity.gatepass.bulk;

/**
 * One row lifted out of the spreadsheet, before any validation.
 *
 * rowNumber is the number the USER sees in Excel - 1-based, header included.
 * So the first data row is 2, not 0 and not 1. Every error message quotes this
 * number, and "row 34: invalid email" has to mean row 34 in the file they are
 * looking at. Reporting a zero-based index here is the single most common way
 * to make an error report useless.
 *
 * Everything arrives as trimmed text. Type conversion is not this class's job:
 * a phone number typed into a General-formatted cell comes back from POI as
 * 9.1234567890E9, and deciding what to do about that belongs in the parser,
 * not in the record that holds the result.
 */
public record ParsedRow(
        int rowNumber,
        String name,
        String email,
        String phone,
        String purpose
) {

    /** True when every cell in the row was blank - a trailing row Excel kept. */
    public boolean isEmpty() {
        return blank(name) && blank(email) && blank(phone) && blank(purpose);
    }

    /** Lowercased email, for duplicate detection and identity matching. */
    public String emailKey() {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
