package com.perimity.guard.dto.response;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import com.perimity.guard.document.enums.ScanResult;
import java.time.LocalDateTime;

/**
 * One row of the digital gate register.
 *
 * Denied attempts appear here exactly like successful ones. That is deliberate:
 * the refusals are half the value of the log, and a paper register never
 * recorded them at all.
 *
 * deviceInfo is NOT exposed. It holds a device fingerprint and an IP address,
 * which the history screen does not need and which should not travel further
 * than the collection it is stored in.
 */
public record EntryLogResponse(
        String id,
        Long campusId,
        Long gateId,
        String gateName,
        Long guardUserId,
        String sessionId,
        Long passId,
        Long holderUserId,
        String holderName,
        PassType passType,
        Long eventId,
        Long attributedEventId,
        boolean eventAttributed,
        ScanResult scanResult,
        DenialReason denialReason,
        LocalDateTime scannedAt,
        String scanDate
) {

    public static EntryLogResponse from(EntryLog e) {
        return new EntryLogResponse(
                e.getId(), e.getCampusId(), e.getGateId(), e.getGateName(),
                e.getGuardUserId(), e.getSessionId(), e.getPassId(),
                e.getHolderUserId(), e.getHolderName(), e.getPassType(),
                e.getEventId(), e.getAttributedEventId(), e.isEventAttributed(),
                e.getScanResult(), e.getDenialReason(), e.getScannedAt(), e.getScanDate());
    }
}
