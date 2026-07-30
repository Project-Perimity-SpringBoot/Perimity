package com.perimity.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Result of POST /api/internal/auth/blocklist/screen.
 *
 * FR-BLK-4 IS THE WHOLE DESIGN OF THIS RECORD. A refused row carries a verdict
 * and nothing else - no reason, no "blocklisted since", no hint that a blocklist
 * was even consulted. The reason column exists in the blocklist table so a
 * Campus Admin can defend the decision six months later; it must never travel
 * back down a path that ends in an error report the uploader can read, because
 * the uploader may be the blocked person's host, or the blocked person.
 *
 * REFUSED, not BLOCKED, for the same reason. The bulk engine writes this word
 * into a report a human reads. "Blocked" invites the follow-up question this
 * requirement exists to prevent.
 */
@Schema(description = "Per-row screening verdicts. A refused row carries no reason, by design.")
public record BulkScreenResponseDto(

        @Schema(description = "Rows submitted", example = "600")
        int totalRows,

        @Schema(description = "Rows that may proceed", example = "580")
        int allowedCount,

        @Schema(description = "Rows that must be skipped", example = "20")
        int refusedCount,

        @Schema(description = "One verdict per submitted row, in the order submitted")
        List<RowVerdict> verdicts
) {

    public static BulkScreenResponseDto of(List<RowVerdict> verdicts) {
        int refused = (int) verdicts.stream().filter(v -> !v.allowed()).count();
        return new BulkScreenResponseDto(
                verdicts.size(), verdicts.size() - refused, refused, verdicts);
    }

    @Schema(description = "One row's verdict")
    public record RowVerdict(

            @Schema(description = "The spreadsheet row number the caller supplied", example = "34")
            Integer rowNumber,

            @Schema(description = "The email screened, echoed back so the caller can match "
                    + "rows without relying on list order", example = "a.visitor@example.com")
            String email,

            @Schema(description = "True when this row may proceed")
            boolean allowed,

            /**
             * The only two values, and neither explains itself:
             *   OK       - proceed
             *   REFUSED  - do not create anything for this row
             *
             * Deliberately not an enum with a REASON field attached. An enum
             * that could grow a BLOCKLISTED_EMAIL / BLOCKLISTED_PHONE split
             * would leak which field matched, and from there which of the two
             * an administrator had typed in.
             */
            @Schema(description = "OK or REFUSED. Never says why.", example = "OK")
            String verdict
    ) {

        public static RowVerdict ok(Integer rowNumber, String email) {
            return new RowVerdict(rowNumber, email, true, "OK");
        }

        public static RowVerdict refused(Integer rowNumber, String email) {
            return new RowVerdict(rowNumber, email, false, "REFUSED");
        }
    }
}
