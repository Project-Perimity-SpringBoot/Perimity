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
import com.perimity.gatepass.messaging.QrJobPublisher;
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
    private final QrJobPublisher qrJobPublisher;

    public GatePassService(GatePassRepository passRepository, EventRepository eventRepository,
                           QrJobPublisher qrJobPublisher) {
        this.passRepository = passRepository;
        this.eventRepository = eventRepository;
        this.qrJobPublisher = qrJobPublisher;
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

        /*
         * ==============================================================
         *  ONE STANDING PASS PER STUDENT, however many times this is called
         * ==============================================================
         * A student's everyday pass is now issued automatically the moment
         * their account is made, and the callers that do it are not
         * single-shot: an import batch can be resumed, the Add Student
         * profile call can be retried, and a re-verification asks again.
         * Without this check each of those mints another QR, the holder's
         * My Pass screen fills up with passes that all work, and a revoke
         * only kills whichever one somebody happened to revoke.
         *
         * The existing pass is returned rather than an error thrown. Every
         * caller here is asking "make sure this person has their pass", and
         * they already do - that is success, not a conflict.
         *
         * EVENT passes are excluded: their duplicate rule is the one above,
         * which is per-event and correctly refuses rather than returns.
         */
        if (dto.getPassType() == PassType.DAILY
                && dto.getEventId() == null
                && dto.getVisitorRequestId() == null) {

            Optional<GatePass> standing =
                    passRepository.findLiveStandingDailyPass(dto.getHolderUserId());

            if (standing.isPresent()) {
                log.info("Holder {} already has standing pass {} ({}) - reusing it.",
                        dto.getHolderUserId(), standing.get().getId(), standing.get().getStatus());
                return GatePassResponse.from(standing.get());
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

        // Published AFTER this transaction commits, so qr-service cannot read
        // the pass before the INSERT is visible. See QrJobPublisher.
        qrJobPublisher.publishAfterCommit(saved);

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

        GatePass saved = passRepository.save(pass);
        qrJobPublisher.publishAfterCommit(saved);
        return saved;
    }

    /**
     * Re-queue a pass whose generation never finished.
     *
     * A broker outage at the wrong moment leaves a pass at PENDING with nobody
     * coming back for it. Without this the only fix is a database edit.
     */
    @Transactional
    public GatePassResponse republishGenerationJob(Long campusId, Long id) {
        GatePass pass = require(campusId, id);

        if (pass.getStatus() != PassStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending pass can be re-queued. This one is "
                            + pass.getStatus().name().toLowerCase() + ".");
        }

        qrJobPublisher.publishAfterCommit(pass);
        return GatePassResponse.from(pass);
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
     * The other half of pauseAllForHolder, which did not exist.
     *
     * ==================================================================
     *  WHY THIS IS A BUG FIX AND NOT A FEATURE
     * ==================================================================
     * A sensitive profile edit paused every pass a person held, and NOTHING in
     * the product ever moved one back. Not verification, not any screen -
     * gatepassApi.changeStatus had no caller anywhere in the frontend. The
     * student's own pass page meanwhile promised "staff re-verify and it
     * resumes - you keep the same QR code and nothing is reissued", which was
     * true of the intent and false of the code.
     *
     * So a student who uploaded their photo lost their pass permanently, and
     * the only ways back were a raw API call or an UPDATE statement.
     *
     * ==================================================================
     *  ONLY PAUSED PASSES MOVE, AND ONLY TO ACTIVE
     * ==================================================================
     * The mirror of pause. A REVOKED pass must never come back this way -
     * somebody revoked it deliberately and a profile approval is not a reversal
     * of that decision. EXPIRED is past its date and resuming it would open the
     * gate for a pass that ran out. PENDING has no QR yet and is qr-service's
     * to finish.
     *
     * canTransitionTo is still consulted per pass rather than trusted from the
     * query, because the status could have changed between the read and the
     * write, and the state machine is the authority on this - not the filter
     * that selected the rows.
     */
    @Transactional
    public List<GatePassResponse> resumeAllForHolder(Long holderUserId, String reason,
                                                     Long changedBy) {
        List<GatePass> paused = passRepository
                .findByHolderUserIdAndStatusOrderByCreatedAtDesc(holderUserId, PassStatus.PAUSED);

        List<GatePass> resumed = paused.stream()
                .filter(p -> p.getStatus().canTransitionTo(PassStatus.ACTIVE))
                .peek(p -> applyTransition(p, PassStatus.ACTIVE, reason, changedBy))
                .toList();

        passRepository.saveAll(resumed);

        log.info("Resumed {} pass(es) for holder {} - {}", resumed.size(), holderUserId, reason);

        return resumed.stream().map(GatePassResponse::from).toList();
    }

    /**
     * Revoke every live pass issued for an event. Called by EventService when
     * an event is cancelled.
     *
     * This closes the TODO that has sat in EventService.cancel since Day 6,
     * and it is a real gate defect rather than housekeeping: without it,
     * cancelling an event leaves every pass ACTIVE and the gate opens for all
     * of them.
     *
     * PENDING passes are revoked too, and that is the part worth thinking
     * about. A PENDING pass is one whose QR is still being generated. Leave it
     * alone and qr-service finishes the job, calls activate, and the pass goes
     * GREEN minutes after the event was called off. activate() already refuses
     * to move a REVOKED pass - revoking now is what makes that refusal fire.
     *
     * EXPIRED and REVOKED are skipped; both are terminal and canTransitionTo
     * would reject them anyway.
     */
    @Transactional
    public int revokeAllForEvent(Long eventId, String reason, Long actorUserId) {
        List<GatePass> live = passRepository.findByEventId(eventId).stream()
                .filter(p -> p.getStatus() == PassStatus.ACTIVE
                          || p.getStatus() == PassStatus.PENDING
                          || p.getStatus() == PassStatus.PAUSED)
                .toList();

        live.forEach(p -> applyTransition(p, PassStatus.REVOKED, reason, actorUserId));
        passRepository.saveAll(live);

        log.info("Revoked {} pass(es) for cancelled event {}", live.size(), eventId);
        return live.size();
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

    /**
     * Pass lookup for another SERVICE, not scoped to a campus.
     *
     * Day 11. guard-service calls this on every single scan: the QR decrypts
     * to a pass id, and before the gate opens it has to know the pass is still
     * ACTIVE and still in date. Palash's HttpPassVerificationClient has been
     * calling it since Day 9 and getting 404, because it did not exist.
     *
     * WHY NO campusId PARAMETER, when every other read here is campus-scoped:
     * the caller does not have one. A guard scans a QR; the token yields a pass
     * id and nothing else. Requiring a campus would mean guard-service guessing
     * at the answer it is asking the question to find out.
     *
     * That is safe here and would NOT be safe on a public endpoint. This is
     * reachable only behind InternalApiKeyFilter - a service, never a browser.
     * The campus still gets checked: the response carries campusId, and
     * ScanService compares it against the guard's open session, so a pass from
     * another campus is denied at the gate rather than here.
     */
    @Transactional(readOnly = true)
    public GatePassResponse getForInternal(Long id) {
        GatePass pass = passRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Pass", id));
        return withEventName(pass);
    }

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
