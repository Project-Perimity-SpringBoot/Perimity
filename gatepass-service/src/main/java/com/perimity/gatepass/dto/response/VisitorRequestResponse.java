package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.Gender;
import com.perimity.gatepass.entity.enums.IdType;
import com.perimity.gatepass.entity.enums.PurposeType;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.entity.enums.VisitorType;
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
        PurposeType purposeType,
        VisitorType visitorType,
        Gender gender,
        LocalDate dateOfBirth,
        IdType idType,
        String idNumber,
        Long hostUserId,
        Long eventId,
        LocalDate visitFrom,
        LocalDate visitTo,
        boolean otpVerified,
        Long visitorUserId,
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
                e.getPurposeType(),
                e.getVisitorType(),
                e.getGender(),
                e.getDateOfBirth(),
                e.getIdType(),
                e.getIdNumber(),
                e.getHostUserId(),
                e.getEventId(),
                e.getVisitFrom(),
                e.getVisitTo(),
                e.isOtpVerified(),
                e.getVisitorUserId(),
                e.getStatus(),
                e.getReviewedBy(),
                e.getReviewedAt(),
                e.getRejectReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
