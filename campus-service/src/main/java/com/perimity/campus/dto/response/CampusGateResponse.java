package com.perimity.campus.dto.response;

import com.perimity.campus.entity.CampusGate;
import java.time.LocalDateTime;

/** Read model for one physical gate. This is what the guard's gate picker lists. */
public record CampusGateResponse(
        Long id,
        Long campusId,
        String name,
        String location,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CampusGateResponse from(CampusGate e) {
        return new CampusGateResponse(
                e.getId(),
                e.getCampusId(),
                e.getName(),
                e.getLocation(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
