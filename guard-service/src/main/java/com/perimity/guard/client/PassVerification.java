package com.perimity.guard.client;

import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import java.time.LocalDate;

/**
 * What gatepass-service and qr-service between them can tell us about a scanned
 * token.
 *
 * Deliberately a plain record with no Spring or Mongo annotations: it is a
 * cross-service contract, not a stored document.
 */
public record PassVerification(
        boolean decoded,
        DenialReason denialReason,
        Long passId,
        Long holderUserId,
        String holderName,
        Long campusId,
        PassType passType,
        Long eventId,
        LocalDate validFrom,
        LocalDate validTo,
        String tokenFingerprint
) {

    public static PassVerification undecodable(String fingerprint) {
        return new PassVerification(false, DenialReason.INVALID_TOKEN,
                null, null, null, null, null, null, null, null, fingerprint);
    }
}
