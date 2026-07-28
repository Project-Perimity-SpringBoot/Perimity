package com.perimity.campus.dto.response;

/**
 * Platform-level counts for the Super Admin dashboard.
 *
 * Only counts campus-service owns. Pass and scan totals live in other services
 * and must be fetched from them - reading another service's database directly
 * is the one thing the architecture forbids.
 */
public record CampusStatsResponse(
        long totalCampuses,
        long activeCampuses,
        long inactiveCampuses
) {

    public static CampusStatsResponse of(long total, long active) {
        return new CampusStatsResponse(total, active, Math.max(0, total - active));
    }
}
