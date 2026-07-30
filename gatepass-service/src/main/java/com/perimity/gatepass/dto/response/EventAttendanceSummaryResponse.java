package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.Event;
import java.time.LocalDate;
import java.util.List;

/**
 * The gatepass half of the organiser attendance view (Screen 12).
 *
 * ==========================================================================
 *  WHY THIS DOES NOT CONTAIN ATTENDANCE NUMBERS
 * ==========================================================================
 *
 * Screen 12 shows:
 *
 *     AI Summit, Aug 10-12
 *     Registered:     600     <- THIS SERVICE. A pass is a registration.
 *     Attended Day 1: 543     <- guard-service. An entry log is an attendance.
 *     Never showed:    41     <- arithmetic on the two
 *
 * Those two numbers live in two different databases and neither service may
 * read the other's. So the screen makes TWO calls and does the subtraction
 * itself:
 *
 *     GET /api/gatepass/events/{id}/attendance-summary        (this)
 *     GET /api/guard/entry-logs/events/{id}/attendance        (Palash)
 *
 * gatepass-service does NOT proxy Palash's endpoint, and that is deliberate
 * rather than lazy. His endpoint is guarded by
 * hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN') and guard-service has no
 * internal API at all - a service-to-service call carries an API key, not a
 * staff JWT, so it could never satisfy that check. Adding an internal endpoint
 * purely so this service could relay a number would put a second copy of the
 * attendance logic behind a second authorisation model, for no gain.
 *
 * Palash designed for this: his EventAttendanceResponse takes registeredCount
 * as a parameter from the caller, with a comment saying that number lives here.
 * The browser holds a JWT that satisfies both endpoints. Composition in the
 * client is the correct seam.
 *
 * registeredByStatus is included so the organiser can see WHY registered and
 * attended differ. 600 registered with 40 still PENDING is a generation
 * backlog, not 40 people who did not turn up, and those two look identical if
 * all you have is a single total.
 */
public record EventAttendanceSummaryResponse(

        Long eventId,
        String eventName,
        LocalDate validFrom,
        LocalDate validTo,
        boolean cancelled,

        /** Every pass ever issued for this event, including revoked ones. */
        long totalPasses,

        /**
         * The number Screen 12 labels "Registered", and the number to hand to
         * guard-service as registeredCount.
         *
         * Excludes REVOKED. Someone whose pass was cancelled was not registered
         * for the purposes of an attendance rate - counting them would make
         * every cancelled registration look like a no-show and drag the
         * percentage down for a reason that has nothing to do with attendance.
         */
        long registeredCount,

        /** Breakdown by pass status, so a backlog is distinguishable from absence. */
        List<StatusCount> registeredByStatus,

        /** Day-by-day list of the event window, for the frontend to align bars against. */
        List<LocalDate> eventDays
) {

    public record StatusCount(String status, long count) { }

    public static EventAttendanceSummaryResponse of(Event event,
                                                    long totalPasses,
                                                    long registeredCount,
                                                    List<StatusCount> byStatus) {
        List<LocalDate> days = event.getValidFrom()
                .datesUntil(event.getValidTo().plusDays(1))
                .toList();

        return new EventAttendanceSummaryResponse(
                event.getId(),
                event.getName(),
                event.getValidFrom(),
                event.getValidTo(),
                event.isCancelled(),
                totalPasses,
                registeredCount,
                byStatus == null ? List.of() : List.copyOf(byStatus),
                days);
    }
}
