package com.perimity.gatepass.dto.response;

/**
 * One bad row from a bulk sheet.
 *
 * rowNumber is the spreadsheet row the user actually sees, header included, so
 * "row 34" in the error report means row 34 in their file. Do not send the
 * zero-based index.
 */
public record RowErrorResponse(
        int rowNumber,
        String email,
        String reason
) {

    public static RowErrorResponse of(int rowNumber, String email, String reason) {
        return new RowErrorResponse(rowNumber, email, reason);
    }
}
