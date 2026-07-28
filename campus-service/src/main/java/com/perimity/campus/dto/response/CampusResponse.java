package com.perimity.campus.dto.response;

import com.perimity.campus.entity.Campus;
import java.time.LocalDateTime;

/**
 * Read model for a campus.
 *
 * A record, not a Lombok class: response DTOs are immutable and never validated,
 * so setters buy nothing. Request DTOs stay classes to match the entity style
 * and to keep cross-field constraints straightforward.
 *
 * gateCount needs CampusGateRepository.countByCampusIdAndActiveTrue, so it is
 * supplied by the caller rather than triggering a query inside a mapper.
 */
public record CampusResponse(
        Long id,
        String code,
        String name,
        String address,
        String contactEmail,
        String contactPhone,
        String logoS3Key,
        Long adminUserId,
        boolean active,
        long activeGateCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CampusResponse from(Campus e) {
        return from(e, 0L);
    }

    public static CampusResponse from(Campus e, long activeGateCount) {
        return new CampusResponse(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getAddress(),
                e.getContactEmail(),
                e.getContactPhone(),
                e.getLogoS3Key(),
                e.getAdminUserId(),
                e.isActive(),
                activeGateCount,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
