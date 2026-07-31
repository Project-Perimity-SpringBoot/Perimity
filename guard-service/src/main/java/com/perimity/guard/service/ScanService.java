package com.perimity.guard.service;

import com.perimity.guard.client.CampusConfigClient;
import com.perimity.guard.client.HolderProfileClient;
import com.perimity.guard.client.PassVerification;
import com.perimity.guard.client.PassVerificationClient;
import com.perimity.guard.client.RepeatEntryPolicy;
import com.perimity.guard.client.RunningEventClient;
import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.dto.request.ScanRequestDto;
import com.perimity.guard.dto.response.ScanResponse;
import com.perimity.guard.repository.EntryLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/**
 * The gate.
 *
 * Every path through this class writes exactly one EntryLog document, including
 * every refusal. A denied attempt that leaves no trace is the failure mode the
 * paper register had, and removing it is most of the reason this product exists.
 *
 * The branching is invisible to the guard: one scan, one colour, one line of
 * text.
 */
@Service
public class ScanService {

    private static final DateTimeFormatter SCAN_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EntryLogRepository entryLogRepository;
    private final ScanSessionService sessionService;
    private final PassVerificationClient passVerification;
    private final RunningEventClient runningEvents;
    private final CampusConfigClient campusConfig;
    private final HolderProfileClient holderProfiles;

    public ScanService(EntryLogRepository entryLogRepository,
                       ScanSessionService sessionService,
                       PassVerificationClient passVerification,
                       RunningEventClient runningEvents,
                       CampusConfigClient campusConfig,
                       HolderProfileClient holderProfiles) {
        this.entryLogRepository = entryLogRepository;
        this.sessionService = sessionService;
        this.passVerification = passVerification;
        this.runningEvents = runningEvents;
        this.campusConfig = campusConfig;
        this.holderProfiles = holderProfiles;
    }

    /**
     * guardUserId is a parameter, not a field on the DTO. It arrives from the
     * verified JWT via the controller. A scan that could name its own guard is a
     * register anyone can forge.
     */
    public ScanResponse scan(ScanRequestDto dto, Long guardUserId) {
        // Gate and campus come from here, never from the request. This single
        // lookup is what makes the entry log evidence rather than a claim.
        ScanSession session = sessionService.requireOpenSession(guardUserId);

        LocalDateTime now = LocalDateTime.now();
        PassVerification pass = passVerification.verify(dto.getToken());

        // 1. The token could not be decoded at all.
        if (!pass.decoded()) {
            return deny(session, dto, pass, DenialReason.INVALID_TOKEN, now);
        }

        // 2. The pass exists but its status forbids entry.
        if (pass.denialReason() != null) {
            return deny(session, dto, pass, pass.denialReason(), now);
        }

        // 3. Right pass, wrong institution. Checked against the SESSION's
        //    campus, so a valid pass from another campus cannot be waved
        //    through by a guard who happens to be standing here.
        if (!session.getCampusId().equals(pass.campusId())) {
            return deny(session, dto, pass, DenialReason.WRONG_CAMPUS, now);
        }

        // 4. Outside the validity window. A null validTo is a standing pass
        //    with no end date - the normal case for a student.
        LocalDate today = now.toLocalDate();
        boolean beforeStart = pass.validFrom() != null && today.isBefore(pass.validFrom());
        boolean afterEnd = pass.validTo() != null && today.isAfter(pass.validTo());
        if (beforeStart || afterEnd) {
            return deny(session, dto, pass, DenialReason.OUT_OF_DATE_RANGE, now);
        }

        // 5. Entry is permitted. Has this person already been through today?
        //
        //    A repeat is NEVER a refusal. The paper register had multiple lines
        //    for the same person on the same day and so does this one - the only
        //    question is whether the guard is told. FR-SCAN-8 leaves that to the
        //    campus, and the entry is logged either way.
        ScanResult result = ScanResult.ALLOWED;
        if (alreadyEnteredToday(pass.holderUserId(), session.getCampusId(), today)) {
            result = campusConfig.repeatEntryPolicy(session.getCampusId()) == RepeatEntryPolicy.AMBER
                    ? ScanResult.AMBER
                    : ScanResult.ALLOWED;
        }

        // 6. Decide which event, if any, gets the credit.
        Long attributedEventId = attributeEvent(pass);

        EntryLog log = entryLogRepository.save(
                baseLog(session, dto, pass, now)
                        .scanResult(result)
                        .attributedEventId(attributedEventId)
                        .build());

        sessionService.recordScan(session, result);

        // 7. Only now, once entry is decided, fetch the face.
        //
        //    Deliberately after the decision and not before: a refused scan never
        //    pays for this call, and a user-service outage cannot influence who
        //    gets in. FR-SCAN-9 wants the photo so the guard can check the face
        //    against the pass - which is the only mitigation for the attack the
        //    QR design knowingly permits, someone holding a screenshot of
        //    somebody else's valid pass.
        String photoKey = holderProfiles.profileFor(pass.holderUserId())
                .map(HolderProfileClient.HolderProfile::photoKey)
                .orElse(null);

        // eventName still needs a gatepass-service lookup - the running-event
        // endpoint returns an id only. The message degrades to a plain welcome,
        // which is correct, just less warm than "Welcome to [Event]".
        return result == ScanResult.AMBER
                ? ScanResponse.repeatEntry(log, null, photoKey)
                : ScanResponse.allowed(log, null, photoKey);
    }

    /**
     * Has this holder already been let in at this campus today?
     *
     * Campus-scoped on purpose: entering another campus this morning has no
     * bearing on whether this is a repeat here.
     *
     * Counts refusals as well as entries - deliberately. A person who was turned
     * away at 9am and admitted at 10am has been seen twice today, and telling the
     * guard so is the entire point of amber. Filtering to allowed scans only
     * would hide exactly the case worth flagging.
     */
    private boolean alreadyEnteredToday(Long holderUserId, Long campusId, LocalDate today) {
        if (holderUserId == null) {
            return false;
        }
        return entryLogRepository.existsByHolderUserIdAndCampusIdAndScannedAtBetween(
                holderUserId, campusId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    /**
     * Behavior 2.
     *
     * An EVENT pass credits its own event. A DAILY pass asks whether this
     * person has an event running today - if so the entry is credited to it
     * anyway.
     *
     * A student attending a programme carries two valid QRs and will scan
     * whichever is on top. The organiser's attendance list must not depend on
     * which one that was, and the guard must never have to ask.
     */
    private Long attributeEvent(PassVerification pass) {
        if (pass.passType() == PassType.EVENT) {
            return pass.eventId();
        }
        return runningEvents.runningEventFor(pass.holderUserId()).orElse(null);
    }

    private ScanResponse deny(ScanSession session, ScanRequestDto dto, PassVerification pass,
                              DenialReason reason, LocalDateTime now) {

        EntryLog log = entryLogRepository.save(
                baseLog(session, dto, pass, now)
                        .scanResult(ScanResult.DENIED)
                        .denialReason(reason)
                        .build());

        sessionService.recordScan(session, ScanResult.DENIED);
        return ScanResponse.denied(log, reason);
    }

    /**
     * Everything common to an allowed and a denied scan.
     *
     * Note what is copied in rather than referenced: holderName and gateName.
     * The log must render years later without calling another service, and
     * without depending on a name that may since have changed.
     */
    private EntryLog.EntryLogBuilder baseLog(ScanSession session, ScanRequestDto dto,
                                             PassVerification pass, LocalDateTime now) {
        return EntryLog.builder()
                .campusId(session.getCampusId())
                .gateId(session.getGateId())
                .gateName(session.getGateName())
                .guardUserId(session.getGuardUserId())
                .sessionId(session.getId())
                .passId(pass.passId())
                .holderUserId(pass.holderUserId())
                .holderName(pass.holderName())
                .passType(pass.passType())
                .eventId(pass.eventId())
                .tokenFingerprint(pass.tokenFingerprint())
                .scannedAt(now)
                .scanDate(now.format(SCAN_DATE))
                .deviceInfo(dto.getDeviceInfo());
    }
}
