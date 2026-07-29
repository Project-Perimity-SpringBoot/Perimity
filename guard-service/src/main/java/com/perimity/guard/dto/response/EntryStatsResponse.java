package com.perimity.guard.dto.response;

import java.time.LocalDateTime;

/**
 * Counts for the campus gate dashboard over a date range.
 *
 * Only what guard-service owns. Pass and profile totals live elsewhere and must
 * be fetched from those services.
 */
public record EntryStatsResponse(
        Long campusId,
        LocalDateTime from,
        LocalDateTime to,
        long allowedCount,
        long deniedCount,
        long totalScans
) {

    public static EntryStatsResponse of(Long campusId, LocalDateTime from, LocalDateTime to,
                                        long allowed, long denied) {
        return new EntryStatsResponse(campusId, from, to, allowed, denied, allowed + denied);
    }
}
