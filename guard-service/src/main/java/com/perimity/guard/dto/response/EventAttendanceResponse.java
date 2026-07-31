package com.perimity.guard.dto.response;

import java.util.List;

/**
 * The organiser attendance view - the strongest demonstration in the product,
 * because a paper register could never produce it.
 *
 *   AI Summit, Aug 10-12
 *   Registered:     600
 *   Attended Day 1: 543  (90.5%)
 *   Attended Day 2: 478  (79.7%)
 *   Never showed:    41
 *
 * Counted on attributedEventId, not eventId. That is what makes Behavior 2
 * work: a student who scanned their DAILY QR during the event is still counted,
 * because the scan was credited to the event at write time.
 *
 * One attendance per person per day - a person stepping out for lunch is not a
 * second attendee.
 *
 * registeredCount comes from the caller. The number of passes issued lives in
 * gatepass-service, and guard-service must never read another service's
 * database to fill in its own report.
 */
public record EventAttendanceResponse(
        Long eventId,
        String eventName,
        long registeredCount,
        long uniqueAttendeeCount,
        long neverShowedCount,
        List<DayAttendance> days
) {

    public record DayAttendance(String scanDate, long attendedCount, double attendancePercent) {
        public static DayAttendance of(String scanDate, long attended, long registered) {
            double pct = registered <= 0 ? 0.0 : Math.round(attended * 1000.0 / registered) / 10.0;
            return new DayAttendance(scanDate, attended, pct);
        }
    }

    public static EventAttendanceResponse of(Long eventId, String eventName,
                                             long registeredCount, long uniqueAttendeeCount,
                                             List<DayAttendance> days) {
        return new EventAttendanceResponse(
                eventId, eventName, registeredCount, uniqueAttendeeCount,
                Math.max(0, registeredCount - uniqueAttendeeCount),
                days == null ? List.of() : List.copyOf(days));
    }
}
