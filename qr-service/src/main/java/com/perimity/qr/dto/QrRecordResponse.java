package com.perimity.qr.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * What GET /api/qr/{passId} returns.
 *
 * Deliberately does NOT include tokenHash or the QrRecord's own id - callers
 * outside this service never need either, and there's no reason to put a
 * hash on the wire that doesn't have to be there.
 */
@Getter
@Builder
@AllArgsConstructor
public class QrRecordResponse {

    private Long passId;
    private Long campusId;
    private String qrKey;
    private String pdfKey;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;
}
