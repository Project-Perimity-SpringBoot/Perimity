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
        String purpose,
        Details details
) {

    /**
     * The rest of a Google Form responses sheet.
     *
     * ======================================================================
     *  WHY THESE ARE ALL OPTIONAL AND WHY THEY ARE IN A NESTED RECORD
     * ======================================================================
     * A faculty member running an event reuses whatever form they already
     * have. The student intake form asks for date of birth, address, roll
     * number and a passport photo; a guest-lecture RSVP form asks for a name
     * and an email and nothing else. BOTH have to upload cleanly, so not one
     * field here may be required - name and email remain the only two the
     * sheet must carry.
     *
     * They live in a nested record rather than as nine more components on
     * ParsedRow so that the row's identity fields stay readable at the call
     * site, and so that "was anything else supplied at all" is one null check.
     *
     * Nothing here is trusted as an identifier. rollNo and department are
     * recorded as the sheet spelled them; they are NOT used to match an
     * attendee to an existing student, because a typo in a roll number would
     * then hand one person another person's pass. Matching is by email only,
     * which auth-service owns.
     */
    public record Details(
            String firstName,
            String middleName,
            String lastName,
            String dateOfBirth,
            String gender,
            String address,
            String rollNo,
            String department,
            String photoLink
    ) {

        /** True when the sheet carried none of these columns, or all of them blank. */
        public boolean isEmpty() {
            return blank(firstName) && blank(middleName) && blank(lastName)
                    && blank(dateOfBirth) && blank(gender) && blank(address)
                    && blank(rollNo) && blank(department) && blank(photoLink);
        }
    }

    /**
     * The four-column form, for callers and tests that only care about
     * identity. Kept so that a sheet with no extra columns, and every test
     * written before those columns existed, still construct a ParsedRow the
     * short way.
     */
    public ParsedRow(int rowNumber, String name, String email, String phone, String purpose) {
        this(rowNumber, name, email, phone, purpose, null);
    }

    /** True when every cell in the row was blank - a trailing row Excel kept. */
    public boolean isEmpty() {
        return blank(name) && blank(email) && blank(phone) && blank(purpose)
                && (details == null || details.isEmpty());
    }

    /** Lowercased email, for duplicate detection and identity matching. */
    public String emailKey() {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
