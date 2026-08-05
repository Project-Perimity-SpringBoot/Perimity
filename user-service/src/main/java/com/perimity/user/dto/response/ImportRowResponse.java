package com.perimity.user.dto.response;

import com.perimity.user.entity.StudentImportRow;
import com.perimity.user.entity.enums.ImportRowOutcome;

/**
 * One row of an import, for the preview and the result screens.
 *
 * ==========================================================================
 * WHAT IS DELIBERATELY NOT HERE
 * ==========================================================================
 * Date of birth, address and both phone numbers.
 *
 * The preview exists so a faculty member can answer one question - "is this
 * the right sheet, and which rows are broken" - and that needs a name, a roll
 * number and a reason. It does not need two hundred students' dates of birth
 * on one screen.
 *
 * Same reasoning as the student directory, which blanks contact details for
 * exactly this reason: one record at a time is a lookup, all of them at once is
 * an export. A preview table is the easiest thing in the product to screenshot.
 *
 * The values are all still stored and all still imported. They are just not
 * put on a screen that has no use for them.
 */
public record ImportRowResponse(
        Long id,
        int rowNumber,
        String email,
        String fullName,
        String rollNo,
        String departmentLabel,
        boolean hasPhoto,
        ImportRowOutcome outcome,
        String message,
        Long userId
) {

    public static ImportRowResponse from(StudentImportRow e) {
        return new ImportRowResponse(
                e.getId(),
                e.getRowNumber(),
                e.getEmail(),
                displayName(e),
                e.getRollNo(),
                e.getDepartmentLabel(),
                // Whether a link was found, not whether the image downloaded.
                // Before confirm that is all that is known.
                e.getPhotoDriveId() != null,
                e.getOutcome(),
                e.getMessage(),
                e.getUserId());
    }

    /** The form's full name if there is one, otherwise the parts joined. */
    private static String displayName(StudentImportRow e) {
        if (e.getFullName() != null && !e.getFullName().isBlank()) {
            return e.getFullName();
        }
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{e.getFirstName(), e.getMiddleName(), e.getLastName()}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
