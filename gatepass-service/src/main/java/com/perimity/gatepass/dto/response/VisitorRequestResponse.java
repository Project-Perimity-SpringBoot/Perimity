package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.RequestStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model for a visitor request.
 *
 * A record, not a Lombok class: response DTOs are immutable and never validated,
 * so they gain nothing from setters. Request DTOs are classes because
 * @ValidDateRange reads properties through Spring's BeanWrapper, which needs
 * JavaBean getters that records do not produce.
 */
public record VisitorRequestResponse(
        Long id,
        Long campusId,
        String visitorName,
        String visitorEmail,
        String visitorPhone,
        String purpose,
        Long hostUserId,
        Long eventId,
        LocalDate visitFrom,
        LocalDate visitTo,
        boolean otpVerified,
        RequestStatus status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static VisitorRequestResponse from(VisitorRequest e) {
        return new VisitorRequestResponse(
                e.getId(),
                e.getCampusId(),
                e.getVisitorName(),
                e.getVisitorEmail(),
                e.getVisitorPhone(),
                e.getPurpose(),
                e.getHostUserId(),
                e.getEventId(),
                e.getVisitFrom(),
                e.getVisitTo(),
                e.isOtpVerified(),
                e.getStatus(),
                e.getReviewedBy(),
                e.getReviewedAt(),
                e.getRejectReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
