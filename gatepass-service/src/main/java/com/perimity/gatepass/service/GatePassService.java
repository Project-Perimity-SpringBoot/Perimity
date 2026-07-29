package com.perimity.gatepass.service;

import com.perimity.gatepass.dto.request.GatePassCreateDto;
import com.perimity.gatepass.dto.request.GatePassStatusUpdateDto;
import com.perimity.gatepass.dto.request.HolderPauseDto;
import com.perimity.gatepass.dto.request.PassActivationDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pass lifecycle. This is the heart of gatepass-service.
 *
 * One rule governs everything here: a status only changes through
 * PassStatus.canTransitionTo. The enum owns which moves are legal; this class
 * owns whether the caller is allowed to ask, and what else must happen when the
 * move is made. Neither duplicates the other.
 *
 * The legal graph, from the enum:
 *     PENDING -> ACTIVE, REVOKED
 *     ACTIVE  -> PAUSED, EXPIRED, REVOKED
 *     PAUSED  -> ACTIVE, REVOKED
 *     EXPIRED, REVOKED -> nothing. Both are terminal.
 */
@Service
public class GatePassService {

    private static final Logger log = LoggerFactory.getLogger(GatePassService.class);

    private final GatePassRepository passRepository;
    private final EventRepository eventRepository;

    public GatePassService(GatePassRepository passRepository, EventRepository eventRepository) {
        this.passRepository = passRepository;
        this.eventRepository = eventRepository;
    }

    // ------------------------------------------------------------- issue

    /**
     * Issue a pass directly - a student's standing DAILY pass, or one an admin
     * creates by hand.
     *
     * The DTO already proved the DAILY/EVENT shape is coherent. What it could
     * not check is whether the named event exists, and whether this person
     * already holds a pass for it.
     */
    @Transactional
    public GatePassResponse issue(GatePassCreateDto dto) {

        if (dto.getPassType() == PassType.EVENT) {
            Event event = eventRepository.findByIdAndCampusId(dto.getEventId(), dto.getCampusId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Event", dto.getEventId()));

            if (event.isCancelled()) {
                throw new IllegalStateException("That event has been cancelled.");
            }

            // One live event pass per person per event. Without this a second
            // registration puts two QRs in one inbox and the attendance count
            // for that event is wrong from then on.
            if (passRepository.existsByHolderUserIdAndEventIdAndStatusNot(
                    dto.getHolderUserId(), dto.getEventId(), PassStatus.REVOKED)) {
                throw new IllegalStateException(
                        "This person already holds a pass for that event.");
            }

            // The pass window must sit inside the event window. A pass valid
            // beyond the event is a pass that opens the gate after it ends.
            if (dto.getValidFrom().isBefore(event.getValidFrom())
                    || dto.getValidTo().isAfter(event.getValidTo())) {
                throw new IllegalArgumentException(
                        "The pass dates must fall inside the event window, "
                                + event.getValidFrom() + " to " + event.getValidTo() + ".");
            }
        }

        GatePass pass = GatePass.builder()
                .holderUserId(dto.getHolderUserId())
                .holderName(dto.getHolderName().trim())
                .campusId(dto.getCampusId())
                .visitorRequestId(dto.getVisitorRequestId())
                .passType(dto.getPassType())
                .eventId(dto.getEventId())
                .validFrom(dto.getValidFrom())
                .validTo(dto.getValidTo())
                .status(PassStatus.PENDING)
                .build();

        GatePass saved = passRepository.save(pass);

        // TODO Day 8: publish the QR generation job to RabbitMQ here.
        // Until then the pass sits at PENDING until someone calls /activate.
        return GatePassResponse.from(saved);
    }

    /**
     * Issue the pass for an approved visitor request.
     *
     * Moved here from VisitorRequestService, where it lived on Day 5 because
     * this class did not exist yet. Issuance belongs in one place.
     *
     * Idempotent: a retry or a double-click must not put two live QRs in one
     * visitor's inbox.
     */
    @Transactional
    public GatePass issueForApprovedRequest(VisitorRequest request) {
        Optional<GatePass> existing = passRepository.findByVisitorRequestId(request.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean forEvent = request.getEventId() != null;

        GatePass pass = GatePass.builder()
                .holderUserId(request.getVisitorUserId())
                .holderName(request.getVisitorName())
                .campusId(request.getCampusId())
                .visitorRequestId(request.getId())
                .passType(forEvent ? PassType.EVENT : PassType.DAILY)
                .eventId(request.getEventId())
                .validFrom(request.getVisitFrom())
                .validTo(request.getVisitTo())
                .status(PassStatus.PENDING)
                .build();

        return passRepository.save(pass);
    }

    // ---------------------------------------------------- state changes

    /**
     * Pause, resume or revoke.
     *
     * The DTO already restricted the target to ACTIVE, PAUSED or REVOKED -
     * PENDING is set at creation and EXPIRED by the sweep, so neither is
     * something a human asks for. What the DTO could not know is the pass's
     * CURRENT state, which is what decides whether the move is legal.
     */
    @Transactional
    public GatePassResponse changeStatus(Long campusId, Long id, GatePassStatusUpdateDto dto) {
        GatePass pass = require(campusId, id);
        PassStatus from = pass.getStatus();
        PassStatus to = dto.getTargetStatus();

        if (from == to) {
            throw new IllegalStateException("This pass is already " + from.name().toLowerCase() + ".");
        }

        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException(
                    "A " + from.name().toLowerCase() + " pass cannot become "
                            + to.name().toLowerCase() + ". Allowed from here: "
                            + describe(from.allowedNextStates()) + ".");
        }

        applyTransition(pass, to, dto.getReason(), dto.getChangedBy());
        return GatePassResponse.from(passRepository.save(pass));
    }

    /**
     * Writes the state change and the fields that go with it.
     *
     * Kept separate so the sweep and the internal endpoints reuse exactly the
     * same logic. A second place that sets status is a second place that can
     * forget to clear pausedReason.
     */
    private void applyTransition(GatePass pass, PassStatus to, String reason, Long actorUserId) {
        switch (to) {
            case ACTIVE -> {
                // Resuming from PAUSED. The old reason must go, or the UI shows
                // a live pass still displaying why it was once held.
                pass.setPausedReason(null);
            }
            case PAUSED -> pass.setPausedReason(reason);
            case REVOKED -> {
                pass.setRevokedReason(reason);
                pass.setRevokedBy(actorUserId);
                pass.setRevokedAt(LocalDateTime.now());
            }
            case EXPIRED -> {
                // Nothing extra. validTo already says why.
            }
            default -> throw new IllegalStateException("Unsupported target status " + to);
        }
        pass.setStatus(to);
    }

    /**
     * qr-service reports that generation finished. PENDING -> ACTIVE.
     *
     * This is the only path that turns a pass green, and it requires both object
     * keys. An ACTIVE pass with no QR scans fine at the gate but the holder has
     * nothing to present.
     */
    @Transactional
    public GatePassResponse activate(Long id, PassActivationDto dto) {
        GatePass pass = passRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Pass", id));

        // Idempotent. A redelivered RabbitMQ message must not be an error.
        if (pass.getStatus() == PassStatus.ACTIVE) {
            return GatePassResponse.from(pass);
        }

        if (!pass.getStatus().canTransitionTo(PassStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "A " + pass.getStatus().name().toLowerCase()
                            + " pass cannot be activated. It was probably revoked "
                            + "while generation was still running.");
        }

        pass.setQrKey(dto.getQrKey());
        pass.setPdfKey(dto.getPdfKey());
        applyTransition(pass, PassStatus.ACTIVE, null, null);

        return GatePassResponse.from(passRepository.save(pass));
    }

    /**
     * A sensitive profile field changed, so hold everything this person holds
     * (SRS v1.1). Called by user-service.
     *
     * Only ACTIVE passes move. A PENDING pass has no QR out in the world yet,
     * and a REVOKED or EXPIRED one is already dead.
     */
    @Transactional
    public List<GatePassResponse> pauseAllForHolder(Long holderUserId, HolderPauseDto dto) {
        List<GatePass> active = passRepository
                .findByHolderUserIdAndStatusOrderByCreatedAtDesc(holderUserId, PassStatus.ACTIVE);

        active.forEach(p -> applyTransition(p, PassStatus.PAUSED, dto.getReason(), dto.getChangedBy()));
        passRepository.saveAll(active);

        log.info("Paused {} pass(es) for holder {} - {}", active.size(), holderUserId, dto.getReason());

        return active.stream().map(GatePassResponse::from).toList();
    }

    /**
     * The nightly sweep. ACTIVE passes whose validTo is in the past become
     * EXPIRED.
     *
     * A standing DAILY pass has validTo = null and is excluded by the query, so
     * a student's pass never expires on its own - which is the intended
     * behaviour, not an oversight.
     *
     * Returns the count so the scheduler can log something useful.
     */
    @Transactional(readOnly = true)
    public List<Long> findPassesDueToExpire(LocalDate today) {
        return passRepository.findExpiredPasses(today).stream().map(GatePass::getId).toList();
    }

    /**
     * Expires ONE pass, in its own transaction.
     *
     * Deliberately one row per call. The first version saved the whole batch
     * together and it failed in testing: the entity carries @ValidDateRange, so
     * a single row with an inconsistent date range - from a data fix, a
     * migration, or a bad import - throws on flush and rolls back the entire
     * sweep. One bad row meant NOTHING expired that night, silently, and every
     * stale pass stayed green at the gate.
     *
     * Same principle the bulk engine already follows: never let one bad row
     * block the rest.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long passId) {
        GatePass pass = passRepository.findById(passId).orElse(null);
        if (pass == null || pass.getStatus() != PassStatus.ACTIVE) {
            return;
        }
        applyTransition(pass, PassStatus.EXPIRED, null, null);
        passRepository.save(pass);
    }

    // ------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public GatePassResponse getOne(Long campusId, Long id) {
        GatePass pass = require(campusId, id);
        return withEventName(pass);
    }

    /** Every pass this person holds, newest first. The wallet screen. */
    @Transactional(readOnly = true)
    public List<GatePassResponse> byHolder(Long holderUserId) {
        return passRepository.findByHolderUserIdOrderByCreatedAtDesc(holderUserId)
                .stream().map(this::withEventName).toList();
    }

    /** Only the passes that would actually open a gate right now. */
    @Transactional(readOnly = true)
    public List<GatePassResponse> activeByHolder(Long holderUserId) {
        return passRepository
                .findByHolderUserIdAndStatusOrderByCreatedAtDesc(holderUserId, PassStatus.ACTIVE)
                .stream().map(this::withEventName).toList();
    }

    @Transactional(readOnly = true)
    public List<GatePassResponse> byEvent(Long eventId) {
        return passRepository.findByEventId(eventId).stream().map(GatePassResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long countByStatus(Long campusId, PassStatus status) {
        return passRepository.countByCampusIdAndStatus(campusId, status);
    }

    /**
     * Behavior 2 support, for guard-service.
     *
     * The holder scanned some pass. Do they have an event running today? If so
     * the entry is attributed to that event, whichever QR was actually scanned.
     * The guard sees one green light and never learns the difference.
     */
    @Transactional(readOnly = true)
    public Optional<Long> runningEventForHolder(Long holderUserId) {
        return passRepository.findActiveEventIdForHolder(holderUserId, LocalDate.now());
    }

    // ----------------------------------------------------------- helpers

    /** Fills eventName for an EVENT pass. One extra read, only when needed. */
    private GatePassResponse withEventName(GatePass pass) {
        if (pass.getEventId() == null) {
            return GatePassResponse.from(pass);
        }
        String name = eventRepository.findById(pass.getEventId())
                .map(Event::getName)
                .orElse(null);
        return GatePassResponse.from(pass, name);
    }

    private String describe(java.util.Set<PassStatus> states) {
        return states.isEmpty()
                ? "nothing, it is a final state"
                : states.stream().map(s -> s.name().toLowerCase()).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    /** Load or 404. Campus-scoped, so a wrong campus reads as "not found". */
    private GatePass require(Long campusId, Long id) {
        return passRepository.findByIdAndCampusId(id, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Pass", id));
    }
}
