package com.perimity.guard.dto.response;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.ScanResult;
import java.time.LocalDateTime;

/**
 * What the scanner screen shows the guard.
 *
 * The most important response shape in the platform: a guard reads it in about
 * a second, at a gate, with a queue behind them.
 *
 * message is ALWAYS populated, for DENIED as much as ALLOWED. A red light with
 * no reason forces the guard to improvise, which is exactly what the paper
 * register did badly. The wording lives in this one file, so it can never leak
 * internal detail to the person at the gate.
 */
public record ScanResponse(
        ScanResult result,
        String message,
        DenialReason denialReason,
        Long passId,
        Long holderUserId,
        String holderName,
        Long attributedEventId,
        String eventName,
        Long gateId,
        String gateName,
        LocalDateTime scannedAt,
        String entryLogId
) {

    public boolean entryAllowed() {
        return result == ScanResult.ALLOWED;
    }

    /** Normal campus entry, or entry credited to an event. */
    public static ScanResponse allowed(EntryLog log, String eventName) {
        String message = eventName == null
                ? "Welcome, " + safeName(log)
                : "Welcome to " + eventName;
        return new ScanResponse(
                ScanResult.ALLOWED, message, null,
                log.getPassId(), log.getHolderUserId(), log.getHolderName(),
                log.getAttributedEventId(), eventName,
                log.getGateId(), log.getGateName(), log.getScannedAt(), log.getId());
    }

    /** Refused. The reason is always shown, in words a guard can act on. */
    public static ScanResponse denied(EntryLog log, DenialReason reason) {
        return new ScanResponse(
                ScanResult.DENIED, messageFor(reason), reason,
                log.getPassId(), log.getHolderUserId(), log.getHolderName(),
                null, null, log.getGateId(), log.getGateName(),
                log.getScannedAt(), log.getId());
    }

    /**
     * Guard-facing wording for each denial.
     *
     * Deliberately vague about anything internal. "Pass not valid at this
     * campus" tells the guard what to do; it does not tell the person at the
     * gate which campus would have worked.
     */
    private static String messageFor(DenialReason reason) {
        if (reason == null) {
            return "Entry denied";
        }
        return switch (reason) {
            case PASS_EXPIRED      -> "Pass has expired";
            case PASS_REVOKED      -> "Pass has been revoked";
            case PASS_PAUSED       -> "Pass is on hold, awaiting re-approval";
            case PASS_PENDING      -> "Pass is not active yet";
            case INVALID_TOKEN     -> "This QR code is not valid";
            case WRONG_CAMPUS      -> "Pass is not valid at this campus";
            case WRONG_GATE        -> "Pass is not valid at this gate";
            case OUT_OF_DATE_RANGE -> "Pass is not valid today";
        };
    }

    private static String safeName(EntryLog log) {
        return log.getHolderName() == null ? "visitor" : log.getHolderName();
    }
}
