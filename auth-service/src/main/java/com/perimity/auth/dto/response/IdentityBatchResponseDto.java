package com.perimity.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Result of POST /api/internal/auth/users/batch.
 *
 * FAILURE ISOLATION IS THE POINT. Every row gets an outcome and the call returns
 * 200 even when some rows failed. Nothing here throws for a bad row.
 *
 * The alternative - throw on the first refused row - is what the single-row Day 8
 * endpoint does, and it is exactly wrong for a batch. Event_Bulk_Design.md is
 * explicit: "Process all valid rows; never block the batch for a few bad ones."
 * A 600-row upload where row 34 is refused must still produce 599 passes, and
 * the faculty must get a report naming row 34. An exception produces neither.
 *
 * A 200 with 20 REFUSED rows in it is therefore the CORRECT response, not a
 * partial failure. The HTTP status describes whether the batch was processed;
 * the per-row outcomes describe what happened inside it.
 */
@Schema(description = "Per-row identity resolution outcomes. Returns 200 even with refused rows.")
public record IdentityBatchResponseDto(

        @Schema(description = "Rows submitted", example = "600")
        int totalRows,

        @Schema(description = "Existing identities reused - people already in the system",
                example = "102")
        int reusedCount,

        /*
         * "identities", not "VISITOR identities". This record now serves two
         * endpoints: /batch creates visitors, /students creates students. The
         * shape of the answer is identical - per-row outcomes with an id - and
         * duplicating it would mean two things to keep in step for no gain.
         *
         * Which kind of account was created is decided by which endpoint was
         * called, and is not something the caller needs told back.
         */
        @Schema(description = "New identities created", example = "478")
        int createdCount,

        @Schema(description = "Rows refused by the blocklist", example = "15")
        int refusedCount,

        @Schema(description = "Rows skipped because the same email appeared earlier in this "
                + "same batch", example = "5")
        int duplicateCount,

        @Schema(description = "One outcome per submitted row, in the order submitted")
        List<RowResult> results
) {

    public static IdentityBatchResponseDto of(List<RowResult> results) {
        return new IdentityBatchResponseDto(
                results.size(),
                count(results, "REUSED"),
                count(results, "CREATED"),
                count(results, "REFUSED"),
                count(results, "DUPLICATE"),
                results);
    }

    private static int count(List<RowResult> results, String outcome) {
        return (int) results.stream().filter(r -> outcome.equals(r.outcome())).count();
    }

    @Schema(description = "One row's outcome")
    public record RowResult(

            @Schema(description = "The spreadsheet row number the caller supplied", example = "34")
            Integer rowNumber,

            @Schema(description = "The email this row resolved", example = "a.visitor@example.com")
            String email,

            /**
             * REUSED    - an identity already existed; userId is that identity
             * CREATED   - a new VISITOR identity was created; userId is the new one
             * REFUSED   - blocklisted. userId is null. No reason, per FR-BLK-4
             * DUPLICATE - this email already appeared earlier in this same batch;
             *             userId points at the identity that first row resolved
             */
            @Schema(description = "REUSED, CREATED, REFUSED or DUPLICATE", example = "CREATED")
            String outcome,

            @Schema(description = "The resolved identity. Null only for REFUSED.", example = "412",
                    nullable = true)
            Long userId
    ) {

        public static RowResult reused(Integer rowNumber, String email, Long userId) {
            return new RowResult(rowNumber, email, "REUSED", userId);
        }

        public static RowResult created(Integer rowNumber, String email, Long userId) {
            return new RowResult(rowNumber, email, "CREATED", userId);
        }

        /** No reason field, and there must never be one. FR-BLK-4. */
        public static RowResult refused(Integer rowNumber, String email) {
            return new RowResult(rowNumber, email, "REFUSED", null);
        }

        public static RowResult duplicate(Integer rowNumber, String email, Long userId) {
            return new RowResult(rowNumber, email, "DUPLICATE", userId);
        }
    }
}
