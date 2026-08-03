package com.perimity.guard.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.guard.document.enums.ScanResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Filter for the entry log - the searchable replacement for flipping back
 * through a paper register.
 *
 * The 90-day cap is not arbitrary. entry_logs is the highest-volume collection
 * in the platform; an unbounded range on a campus with millions of documents is
 * how a demonstration becomes a timeout in front of an examiner.
 *
 * ==========================================================================
 * NOTE WHAT IS NOT HERE: campusId
 * ==========================================================================
 * It used to be a field, @NotNull and @Positive, supplied by the caller. That
 * meant any authenticated guard or campus admin could POST
 *     { "campusId": <somebody else's campus>, ... }
 * and read another campus's entire gate register - who entered, when, at which
 * gate. On a multi-tenant product that is not an IDOR, it is a tenancy breach.
 *
 * This is the SAME mistake ScanRequestDto already documents for guardUserId:
 * "any caller could post a scan as any guard". That one was found and the field
 * was deleted rather than validated. The reads kept it for another few weeks.
 *
 * Validating it would have been weaker. A check can be forgotten on the next
 * endpoint; a field that does not exist cannot be trusted by accident. The
 * campus now comes from the caller's token in EntryLogController, and is passed
 * to EntryLogService as a required argument - so omitting it is a compile
 * error, not a silent hole.
 */
@Schema(description = "Search the gate entry log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryLogFilterDto {

    @NotNull(message = "A start date is required")
    @Schema(example = "2026-08-01T00:00:00")
    private LocalDateTime from;

    @NotNull(message = "An end date is required")
    @Schema(example = "2026-08-12T23:59:59")
    private LocalDateTime to;

    @Schema(description = "ALLOWED or DENIED. Omit for both.", nullable = true)
    private ScanResult scanResult;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "The end of the range must be after the start")
    public boolean isRangeOrdered() {
        return from == null || to == null || !to.isBefore(from);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "The date range may not exceed 90 days")
    public boolean isRangeWithinLimit() {
        if (from == null || to == null || to.isBefore(from)) {
            return true;
        }
        return ChronoUnit.DAYS.between(from, to) <= 90;
    }
}
