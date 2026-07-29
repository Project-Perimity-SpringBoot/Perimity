package com.perimity.gatepass.service;

import com.perimity.gatepass.dto.request.VisitorEmailVerifiedDto;
import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import com.perimity.gatepass.dto.request.VisitorRequestDecisionDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.dto.response.VisitorRequestResponse;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.repository.VisitorRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The visitor approval workflow, and the point where a request becomes a pass.
 *
 * The DTOs already proved the form is well formed - name shape, email shape,
 * dates present and ordered, visit not starting in the past. Everything here
 * needs the database, the clock, or the current state of a row.
 */
@Service
public class VisitorRequestService {

    private final VisitorRequestRepository requestRepository;
    private final GatePassRepository gatePassRepository;
    private final EventRepository eventRepository;

    public VisitorRequestService(VisitorRequestRepository requestRepository,
                                 GatePassRepository gatePassRepository,
                                 EventRepository eventRepository) {
        this.requestRepository = requestRepository;
        this.gatePassRepository = gatePassRepository;
        this.eventRepository = eventRepository;
    }

    // ------------------------------------------------------------- submit

    /**
     * A visitor submits the registration form.
     *
     * Created unverified and without a holder. Nothing else can happen to this
     * row until auth-service confirms the email.
     */
    @Transactional
    public VisitorRequestResponse submit(VisitorRequestCreateDto dto) {

        // One open request per person per campus. Without this, refreshing the
        // form five times gives a host five identical rows to approve, and
        // approving two of them issues two passes to one visitor.
        if (requestRepository.existsByVisitorEmailAndCampusIdAndStatus(
                dto.getVisitorEmail(), dto.getCampusId(), RequestStatus.PENDING)) {
            throw new IllegalStateException(
                    "You already have a request awaiting approval at this campus.");
        }

        // An event request must name an event that exists, belongs to this
        // campus, and is not cancelled. A regex cannot check any of that.
        if (dto.getEventId() != null) {
            Event event = eventRepository.findByIdAndCampusId(dto.getEventId(), dto.getCampusId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Event", dto.getEventId()));
            if (event.isCancelled()) {
                throw new IllegalStateException("That event has been cancelled.");
            }
        }

        VisitorRequest request = VisitorRequest.builder()
                .campusId(dto.getCampusId())
                .visitorName(dto.getVisitorName().trim())
                .visitorEmail(dto.getVisitorEmail().toLowerCase())
                .visitorPhone(dto.getVisitorPhone())
                .purpose(dto.getPurpose())
                .hostUserId(dto.getHostUserId())
                .eventId(dto.getEventId())
                .visitFrom(dto.getVisitFrom())
                .visitTo(dto.getVisitTo())
                .otpVerified(false)
                .status(RequestStatus.PENDING)
                .build();

        return VisitorRequestResponse.from(requestRepository.save(request));
    }

    /**
     * auth-service confirms the email and hands over the visitor's identity.
     *
     * Idempotent: calling it twice is harmless, because a retried message must
     * not be an error. Day 8 makes this a real inter-service call.
     */
    @Transactional
    public VisitorRequestResponse markEmailVerified(Long id, VisitorEmailVerifiedDto dto) {
        VisitorRequest request = requestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Visitor request", id));

        request.setOtpVerified(true);
        request.setVisitorUserId(dto.getVisitorUserId());

        return VisitorRequestResponse.from(requestRepository.save(request));
    }

    // ------------------------------------------------------------- decide

    /**
     * The host approves or rejects.
     *
     * An APPROVED decision is what creates the GatePass, in PENDING status with
     * no QR yet. The QR pipeline picks it up from there on Day 8.
     */
    @Transactional
    public VisitorRequestResponse decide(Long campusId, Long id, VisitorRequestDecisionDto dto) {
        VisitorRequest request = require(campusId, id);

        // Only a PENDING request can be decided. Re-approving an approved
        // request would issue a second pass to the same visitor.
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException(
                    "This request is already " + request.getStatus().name().toLowerCase()
                            + " and cannot be decided again.");
        }

        if (dto.getDecision() == RequestStatus.REJECTED) {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectReason(dto.getRejectReason());
            request.setReviewedBy(dto.getReviewedBy());
            request.setReviewedAt(LocalDateTime.now());
            return VisitorRequestResponse.from(requestRepository.save(request));
        }

        // ---- approval path ----

        // No verified email means no identity, and GatePass.holderUserId is
        // NOT NULL. Refuse clearly here rather than failing at save time.
        if (!request.isOtpVerified() || request.getVisitorUserId() == null) {
            throw new IllegalStateException(
                    "This visitor has not verified their email yet, so no pass can be issued.");
        }

        // Approving a visit that already finished produces a pass that is dead
        // on arrival. Usually means the request sat in the queue too long.
        if (request.getVisitTo().isBefore(LocalDate.now())) {
            throw new IllegalStateException(
                    "The visit window ended on " + request.getVisitTo()
                            + ". Ask the visitor to submit a new request.");
        }

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(dto.getReviewedBy());
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectReason(null);
        requestRepository.save(request);

        issuePassFor(request);

        return VisitorRequestResponse.from(request);
    }

    /**
     * Creates the pass for an approved request.
     *
     * Guarded against producing a second pass, because a retried request or a
     * double-click must not put two live QRs in one visitor's inbox.
     *
     * TODO Day 6: move this into GatePassService once it exists. Issuance
     * belongs there, not here - this is the temporary home, not the design.
     */
    private GatePass issuePassFor(VisitorRequest request) {
        Optional<GatePass> existing = gatePassRepository.findByVisitorRequestId(request.getId());
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

        return gatePassRepository.save(pass);
    }

    /** The pass produced by an approved request, if there is one yet. */
    @Transactional(readOnly = true)
    public GatePassResponse passFor(Long campusId, Long id) {
        require(campusId, id);
        return gatePassRepository.findByVisitorRequestId(id)
                .map(GatePassResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pass has been issued for request " + id + " yet"));
    }

    /** The visitor withdraws before anyone has decided. */
    @Transactional
    public VisitorRequestResponse cancel(Long campusId, Long id) {
        VisitorRequest request = require(campusId, id);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending request can be cancelled. This one is already "
                            + request.getStatus().name().toLowerCase() + ".");
        }

        request.setStatus(RequestStatus.CANCELLED);
        return VisitorRequestResponse.from(requestRepository.save(request));
    }

    // ------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public VisitorRequestResponse getOne(Long campusId, Long id) {
        return VisitorRequestResponse.from(require(campusId, id));
    }

    /** The Campus Admin's queue. Oldest first - a queue, not a feed. */
    @Transactional(readOnly = true)
    public PageResponse<VisitorRequestResponse> byCampusAndStatus(
            Long campusId, RequestStatus status, Pageable pageable) {
        return PageResponse.from(
                requestRepository.findByCampusIdAndStatusOrderByCreatedAtAsc(campusId, status, pageable),
                VisitorRequestResponse::from);
    }

    /** One host's own queue - what a faculty member sees when they log in. */
    @Transactional(readOnly = true)
    public PageResponse<VisitorRequestResponse> byHostAndStatus(
            Long hostUserId, RequestStatus status, Pageable pageable) {
        return PageResponse.from(
                requestRepository.findByHostUserIdAndStatusOrderByCreatedAtAsc(hostUserId, status, pageable),
                VisitorRequestResponse::from);
    }

    /** A visitor's own history, found by the universal key: their email. */
    @Transactional(readOnly = true)
    public List<VisitorRequestResponse> byEmail(String email) {
        return requestRepository.findByVisitorEmailOrderByCreatedAtDesc(email.toLowerCase())
                .stream()
                .map(VisitorRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending(Long campusId) {
        return requestRepository.countByCampusIdAndStatus(campusId, RequestStatus.PENDING);
    }

    /** Load or 404. Campus-scoped, so a wrong campus reads as "not found". */
    private VisitorRequest require(Long campusId, Long id) {
        return requestRepository.findByIdAndCampusId(id, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Visitor request", id));
    }
}
