package com.perimity.gatepass.service;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.request.VisitorEmailVerifiedDto;
import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import com.perimity.gatepass.dto.request.VisitorRequestDecisionDto;
import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.dto.response.VisitorRequestResponse;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.repository.VisitorRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(VisitorRequestService.class);

    /*
     * Campus policy keys. These names are Arham's, seeded by
     * CampusConfigDefaults - they are NOT invented here, and a typo means this
     * service silently falls back to its default forever. Keep them as
     * constants so there is one spelling per key in the whole service.
     */
    private static final String CONFIG_APPROVAL_REQUIRED = "approval.required";
    private static final String CONFIG_DEFAULT_VALIDITY_DAYS = "pass.default.validity.days";

    /*
     * Fallbacks used only when campus-service is unreachable.
     *
     * approval.required defaults to TRUE and that direction matters: if the
     * policy cannot be read, the safe assumption is that a human must approve.
     * Defaulting to false would mean a campus-service outage silently turns
     * every campus into open self-registration.
     */
    private static final boolean APPROVAL_REQUIRED_FALLBACK = true;
    private static final int DEFAULT_VALIDITY_DAYS_FALLBACK = 1;

    private final VisitorRequestRepository requestRepository;
    private final GatePassRepository gatePassRepository;
    private final EventRepository eventRepository;
    private final GatePassService gatePassService;
    private final InternalServiceClient internal;

    public VisitorRequestService(VisitorRequestRepository requestRepository,
                                 GatePassRepository gatePassRepository,
                                 EventRepository eventRepository,
                                 GatePassService gatePassService,
                                 InternalServiceClient internal) {
        this.requestRepository = requestRepository;
        this.gatePassRepository = gatePassRepository;
        this.eventRepository = eventRepository;
        this.gatePassService = gatePassService;
        this.internal = internal;
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

        VisitorRequest saved = requestRepository.save(request);

        // Day 12 - campus policy. A campus that does not require host approval
        // gets its requests approved the moment the email is verified, with no
        // human in the loop. The pass is still not issued here: markEmailVerified
        // is what triggers it, because a pass needs a holder identity and there
        // is not one until the OTP is confirmed.
        if (!approvalRequired(dto.getCampusId())) {
            log.info("Campus {} does not require approval - request {} will auto-approve "
                    + "once the email is verified", dto.getCampusId(), saved.getId());
        }

        return VisitorRequestResponse.from(saved);
    }

    /**
     * Reads approval.required for this campus.
     *
     * Fails CLOSED. If campus-service cannot be reached the answer is "yes,
     * approval is required" - an outage must never silently downgrade a campus
     * to open self-registration.
     */
    private boolean approvalRequired(Long campusId) {
        return internal.configBoolean(campusId, CONFIG_APPROVAL_REQUIRED,
                APPROVAL_REQUIRED_FALLBACK);
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
        requestRepository.save(request);

        // Day 12 - the auto-approve path.
        //
        // Guarded on status == PENDING so a redelivered verification message
        // cannot approve an already-rejected request or issue a second pass.
        // issueForApprovedRequest is itself idempotent on visitorRequestId, so
        // this is belt and braces - which is the right amount for a path that
        // ends in an open gate.
        if (request.getStatus() == RequestStatus.PENDING
                && !approvalRequired(request.getCampusId())) {

            request.setStatus(RequestStatus.APPROVED);
            request.setReviewedAt(LocalDateTime.now());
            // reviewedBy stays NULL on purpose. Nobody reviewed this. Writing a
            // system id here would put a fake approver in the audit trail, and
            // the whole point of the trail is that it is true.
            requestRepository.save(request);

            gatePassService.issueForApprovedRequest(request);

            log.info("Request {} auto-approved - campus {} has approval.required=false",
                    request.getId(), request.getCampusId());
        }

        return VisitorRequestResponse.from(request);
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

        gatePassService.issueForApprovedRequest(request);

        return VisitorRequestResponse.from(request);
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
