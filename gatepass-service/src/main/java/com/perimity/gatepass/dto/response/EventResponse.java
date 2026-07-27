package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.Event;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model for an event.
 *
 * runningToday reuses Event.isRunningOn so the "is this event live" rule lives
 * in exactly one place - the same method Behavior 2 uses at the gate.
 *
 * issuedPassCount needs GatePassRepository.countByEventId, so it is supplied by
 * the caller rather than computed here.
 */
public record EventResponse(
        Long id,
        Long campusId,
        String name,
        String description,
        LocalDate validFrom,
        LocalDate validTo,
        Long createdBy,
        boolean cancelled,
        LocalDateTime cancelledAt,
        boolean runningToday,
        long issuedPassCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EventResponse from(Event e) {
        return from(e, 0L);
    }

    public static EventResponse from(Event e, long issuedPassCount) {
        return new EventResponse(
                e.getId(),
                e.getCampusId(),
                e.getName(),
                e.getDescription(),
                e.getValidFrom(),
                e.getValidTo(),
                e.getCreatedBy(),
                e.isCancelled(),
                e.getCancelledAt(),
                e.isRunningOn(LocalDate.now()),
                issuedPassCount,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
