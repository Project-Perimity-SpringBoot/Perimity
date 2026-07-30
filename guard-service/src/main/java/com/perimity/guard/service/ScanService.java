package com.perimity.guard.service;

import com.perimity.guard.client.PassVerification;
import com.perimity.guard.client.PassVerificationClient;
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

    public ScanService(EntryLogRepository entryLogRepository,
                       ScanSessionService sessionService,
                       PassVerificationClient passVerification,
                       RunningEventClient runningEvents) {
        this.entryLogRepository = entryLogRepository;
        this.sessionService = sessionService;
        this.passVerification = passVerification;
        this.runningEvents = runningEvents;
    }

    public ScanResponse scan(ScanRequestDto dto) {
        // Gate and campus come from here, never from the request. This single
        // lookup is what makes the entry log evidence rather than a claim.
        ScanSession session = sessionService.requireOpenSession(dto.getGuardUserId());

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

        // 5. Allowed. Decide which event, if any, gets the credit.
        Long attributedEventId = attributeEvent(pass);

        EntryLog log = entryLogRepository.save(
                baseLog(session, dto, pass, now)
                        .scanResult(ScanResult.ALLOWED)
                        .attributedEventId(attributedEventId)
                        .build());

        sessionService.recordScan(session, ScanResult.ALLOWED);

        // eventName is null until Day 8 gives us a gatepass-service lookup.
        // The message degrades to a plain welcome, which is still correct.
        return ScanResponse.allowed(log, null);
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
