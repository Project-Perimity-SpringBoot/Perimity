package com.perimity.guard.dto.response;

import java.time.LocalDateTime;

/**
 * Counts for the campus gate dashboard over a date range.
 *
 * Only what guard-service owns. Pass and profile totals live elsewhere and must
 * be fetched from those services.
 *
 * ==========================================================================
 * WHY AMBER IS COUNTED SEPARATELY RATHER THAN FOLDED INTO ALLOWED
 * ==========================================================================
 * An earlier version had two counters, allowed and denied, and summed them for
 * the total. Once AMBER existed those scans belonged to neither bucket, so they
 * vanished from the dashboard entirely - the total silently disagreed with the
 * number of documents in the collection, which is the worst kind of wrong: a
 * report that looks fine and is not.
 *
 * Three counters instead of two, because the guard log screen colour-codes all
 * three and an organiser asking "how many people came back a second time" has a
 * real question. entriesPermitted is the one to read when the question is simply
 * "how many people got in".
 */
public record EntryStatsResponse(
        Long campusId,
        LocalDateTime from,
        LocalDateTime to,
        long allowedCount,
        long amberCount,
        long deniedCount,
        long entriesPermitted,
        long totalScans
) {

    public static EntryStatsResponse of(Long campusId, LocalDateTime from, LocalDateTime to,
                                        long allowed, long amber, long denied) {
        return new EntryStatsResponse(
                campusId, from, to,
                allowed, amber, denied,
                allowed + amber,
                allowed + amber + denied);
    }
}
