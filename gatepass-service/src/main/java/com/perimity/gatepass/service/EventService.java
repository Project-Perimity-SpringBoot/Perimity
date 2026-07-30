package com.perimity.gatepass.service;

import com.perimity.gatepass.dto.request.EventCreateDto;
import com.perimity.gatepass.dto.request.EventUpdateDto;
import com.perimity.gatepass.dto.response.EventResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for events.
 *
 * Division of labour with the DTO layer: EventCreateDto has already proved the
 * input is well formed - name length, dates present, end not before start, no
 * start date in the past. Everything in here needs the database or the current
 * state of a row, which is exactly what a DTO cannot see.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final GatePassRepository gatePassRepository;

    /*
     * NO CIRCULAR DEPENDENCY, and it is worth knowing why: GatePassService
     * does not inject EventService - it talks to EventRepository directly. If
     * anyone later adds EventService to GatePassService, Spring fails at
     * startup with an unsatisfied-dependency loop. The fix at that point is
     * @Lazy on one side, not a redesign.
     */
    private final GatePassService gatePassService;

    public EventService(EventRepository eventRepository,
                        GatePassRepository gatePassRepository,
                        GatePassService gatePassService) {
        this.eventRepository = eventRepository;
        this.gatePassRepository = gatePassRepository;
        this.gatePassService = gatePassService;
    }

    /** Create an event. Rejects a duplicate name on the same campus. */
    @Transactional
    public EventResponse create(EventCreateDto dto) {
        if (eventRepository.existsByCampusIdAndNameIgnoreCase(dto.getCampusId(), dto.getName())) {
            throw new IllegalArgumentException(
                    "An event named \"" + dto.getName() + "\" already exists on this campus");
        }

        Event event = Event.builder()
                .campusId(dto.getCampusId())
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .validFrom(dto.getValidFrom())
                .validTo(dto.getValidTo())
                .createdBy(dto.getCreatedBy())
                .cancelled(false)
                .build();

        return EventResponse.from(eventRepository.save(event), 0L);
    }

    /** One event, scoped to its campus so nobody reads another campus's data. */
    @Transactional(readOnly = true)
    public EventResponse getOne(Long campusId, Long id) {
        Event event = require(campusId, id);
        return EventResponse.from(event, gatePassRepository.countByEventId(id));
    }

    /** Paged list for the Event Management screen. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(Long campusId, Pageable pageable) {
        Page<Event> page = eventRepository.findByCampusIdOrderByValidFromDesc(campusId, pageable);
        return PageResponse.from(page,
                e -> EventResponse.from(e, gatePassRepository.countByEventId(e.getId())));
    }

    /** Every event live today. Behavior 2 and the attendance view both use this. */
    @Transactional(readOnly = true)
    public List<EventResponse> runningToday(Long campusId) {
        return eventRepository.findRunningEvents(campusId, LocalDate.now())
                .stream()
                .map(e -> EventResponse.from(e, gatePassRepository.countByEventId(e.getId())))
                .toList();
    }

    /**
     * Edit an event.
     *
     * Two rules the DTO could not enforce, because both need the saved row:
     *   - a cancelled event is finished and cannot be edited back to life
     *   - the window cannot be shortened once passes have been issued against it,
     *     because that would silently invalidate passes people already hold
     */
    @Transactional
    public EventResponse update(Long campusId, Long id, EventUpdateDto dto) {
        Event event = require(campusId, id);

        if (event.isCancelled()) {
            throw new IllegalStateException("A cancelled event cannot be edited");
        }

        if (!event.getName().equalsIgnoreCase(dto.getName())
                && eventRepository.existsByCampusIdAndNameIgnoreCase(campusId, dto.getName())) {
            throw new IllegalArgumentException(
                    "An event named \"" + dto.getName() + "\" already exists on this campus");
        }

        long issued = gatePassRepository.countByEventId(id);
        boolean windowShrinks = dto.getValidFrom().isAfter(event.getValidFrom())
                || dto.getValidTo().isBefore(event.getValidTo());
        if (issued > 0 && windowShrinks) {
            throw new IllegalStateException(
                    "The event window cannot be shortened: " + issued
                            + " pass(es) have already been issued for the current dates");
        }

        event.setName(dto.getName().trim());
        event.setDescription(dto.getDescription());
        event.setValidFrom(dto.getValidFrom());
        event.setValidTo(dto.getValidTo());

        return EventResponse.from(eventRepository.save(event), issued);
    }

    /**
     * Cancel an event. Never deletes - the attendance record has to survive,
     * and passes already issued still need something to point at.
     */
    @Transactional
    public EventResponse cancel(Long campusId, Long id, Long cancelledBy) {
        Event event = require(campusId, id);

        if (event.isCancelled()) {
            throw new IllegalStateException("This event is already cancelled");
        }

        event.setCancelled(true);
        event.setCancelledAt(LocalDateTime.now());
        eventRepository.save(event);

        // Day 10: the Day 6 TODO is closed.
        //
        // GatePassService owns the revoke path so that revocation goes through
        // the state machine and writes revokedReason, revokedBy and revokedAt
        // exactly once, in one place. Setting status by hand here would be a
        // second place that can forget one of those three fields - which is
        // the same reasoning as applyTransition existing at all.
        int revoked = gatePassService.revokeAllForEvent(
                id, "Event cancelled: " + event.getName(), cancelledBy);

        log.info("Event {} cancelled by user {}, {} pass(es) revoked",
                id, cancelledBy, revoked);

        return EventResponse.from(event, gatePassRepository.countByEventId(id));
    }

    /** Load or 404. Campus-scoped, so a wrong campus reads as "not found". */
    private Event require(Long campusId, Long id) {
        return eventRepository.findByIdAndCampusId(id, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Event", id));
    }
}
