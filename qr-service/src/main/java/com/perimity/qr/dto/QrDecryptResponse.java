package com.perimity.qr.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * What POST /api/internal/qr/decrypt returns to guard-service.
 *
 * Note what is NOT here: no GREEN/RED/AMBER, no holder name, no photo.
 * qr-service answers exactly one question - "is this a real, still-active
 * token, and for which pass?" Whether that pass may enter this gate today is
 * guard-service's decision, and the holder's details are user-service's data.
 * Returning a verdict from here would put the access rule in two places.
 */
@Getter
@Builder
@AllArgsConstructor
public class QrDecryptResponse {

    /** False when the token decrypts but its QrRecord was invalidated. */
    private boolean tokenValid;

    private Long passId;
    private Long campusId;
    private LocalDate validFrom;
    private LocalDate validTo;

    /**
     * DAY 11. Whether today falls inside validFrom..validTo.
     *
     * INFORMATION, NOT A VERDICT - and the distinction is the whole design of
     * this endpoint. A token outside its window is still a genuine token, so
     * tokenValid stays true and this goes false. guard-service decides what to
     * do about it, because it is the service that knows the gate, the session
     * and the pass status.
     *
     * Marking an out-of-window token invalid instead would show the guard
     * "invalid pass" rather than "pass expired", and a visitor turned away with
     * the wrong reason cannot fix the problem.
     */
    private boolean withinValidityWindow;

    /**
     * Populated only when tokenValid is false. One of the
     * DecryptFailureReason names: TOKEN_UNREADABLE, TOKEN_UNKNOWN,
     * TOKEN_SUPERSEDED, TOKEN_MISMATCH.
     *
     * A stable code rather than a sentence, so a caller can branch on it
     * without breaking the day someone fixes a typo in the wording.
     */
    private String reason;
}
