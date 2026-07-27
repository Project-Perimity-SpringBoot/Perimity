package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model for a gate pass.
 *
 * validTo is null for a standing DAILY pass - that is correct, not missing data.
 * eventName is optional: use the two-argument factory when the caller has
 * already loaded the Event, otherwise it stays null rather than triggering an
 * extra query per pass.
 */
public record GatePassResponse(
        Long id,
        Long holderUserId,
        String holderName,
        Long campusId,
        Long visitorRequestId,
        PassType passType,
        Long eventId,
        String eventName,
        LocalDate validFrom,
        LocalDate validTo,
        PassStatus status,
        boolean scannable,
        String revokedReason,
        Long revokedBy,
        LocalDateTime revokedAt,
        String pausedReason,
        String qrKey,
        String pdfKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GatePassResponse from(GatePass e) {
        return from(e, null);
    }

    public static GatePassResponse from(GatePass e, String eventName) {
        return new GatePassResponse(
                e.getId(),
                e.getHolderUserId(),
                e.getHolderName(),
                e.getCampusId(),
                e.getVisitorRequestId(),
                e.getPassType(),
                e.getEventId(),
                eventName,
                e.getValidFrom(),
                e.getValidTo(),
                e.getStatus(),
                e.getStatus() != null && e.getStatus().isScannable(),
                e.getRevokedReason(),
                e.getRevokedBy(),
                e.getRevokedAt(),
                e.getPausedReason(),
                e.getQrKey(),
                e.getPdfKey(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
