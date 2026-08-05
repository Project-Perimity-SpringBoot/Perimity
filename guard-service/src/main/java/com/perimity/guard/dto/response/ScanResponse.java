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
        String entryLogId,

        /**
         * Object-storage key for the holder's photo, or null.
         *
         * Kept alongside holderPhotoUrl rather than replaced by it. The key is
         * stable and identifies the object; the URL expires. Anything that wants
         * to refer to the photo later — an audit view, a support ticket — wants
         * the key, not a link that stopped working minutes after the scan.
         *
         * Not stored on the EntryLog, unlike holderName. A name is a fact about
         * the moment of entry and belongs in an append-only record; a photo key
         * is a pointer to somebody else's mutable storage and does not.
         */
        String holderPhotoKey,

        /**
         * A short-lived signed link to that photo, or null.
         *
         * THIS IS THE ONE THE GATE NEEDS. user-service mints it per read and it
         * was already in the internal summary — guard-service was reading the
         * key beside it and discarding this. The verdict screen could therefore
         * only ever show initials, which meant a guard verified the pass but
         * never the person, and checking the face is the entire mitigation for
         * the attack the QR design does not prevent: a screenshot of somebody
         * else's valid pass.
         *
         * Null is entirely normal. A visitor has no profile, and user-service
         * returns null rather than failing if object storage is slow — a photo
         * must never hold up a queue.
         */
        String holderPhotoUrl
) {

    /** Amber counts. The person walked through. */
    public boolean entryAllowed() {
        return result.permitsEntry();
    }

    /** Normal campus entry, or entry credited to an event. */
    public static ScanResponse allowed(EntryLog log, String eventName,
                                       String photoKey, String photoUrl) {
        String message = eventName == null
                ? "Welcome, " + safeName(log)
                : "Welcome to " + eventName;
        return new ScanResponse(
                ScanResult.ALLOWED, message, null,
                log.getPassId(), log.getHolderUserId(), log.getHolderName(),
                log.getAttributedEventId(), eventName,
                log.getGateId(), log.getGateName(), log.getScannedAt(), log.getId(),
                photoKey, photoUrl);
    }

    /**
     * Amber. The person goes in; the guard is simply told they have been seen
     * already today.
     *
     * denialReason stays null, because nothing was denied. A guard reading this
     * card should see no reason text at all - a reason implies a problem, and
     * there isn't one. Whether a campus wants amber or plain green here is its
     * own choice, read from `repeat_entry_result`.
     */
    public static ScanResponse repeatEntry(EntryLog log, String eventName,
                                       String photoKey, String photoUrl) {
        String message = eventName == null
                ? "Seen already today · " + safeName(log)
                : "Seen already today · " + eventName;
        return new ScanResponse(
                ScanResult.AMBER, message, null,
                log.getPassId(), log.getHolderUserId(), log.getHolderName(),
                log.getAttributedEventId(), eventName,
                log.getGateId(), log.getGateName(), log.getScannedAt(), log.getId(),
                photoKey, photoUrl);
    }

    /**
     * Refused. The reason is always shown, in words a guard can act on.
     *
     * No photo on a refusal - it was never fetched. The guard's job on red is to
     * turn the person away, and a face to compare against would only invite them
     * to second-guess a decision the system already made on better evidence.
     */
    public static ScanResponse denied(EntryLog log, DenialReason reason) {
        return new ScanResponse(
                ScanResult.DENIED, messageFor(reason), reason,
                log.getPassId(), log.getHolderUserId(), log.getHolderName(),
                null, null, log.getGateId(), log.getGateName(),
                log.getScannedAt(), log.getId(),
                // No photo on a refusal - it was never fetched. The guard's job
                // on red is to turn someone away, not to study their face.
                null, null);
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
