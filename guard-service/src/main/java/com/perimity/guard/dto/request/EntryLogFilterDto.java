package com.perimity.guard.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.guard.document.enums.ScanResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * campusId is mandatory for the same reason, and because no legitimate screen
 * queries entry logs platform-wide.
 */
@Schema(description = "Search the gate entry log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryLogFilterDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

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
