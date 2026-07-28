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

    /** Populated only when tokenValid is false, e.g. "Token was re-issued". */
    private String reason;
}
