package com.perimity.user.dto.response;

import com.perimity.user.entity.Department;
import java.time.LocalDateTime;

/** Read model for a department. This is what fills every department dropdown. */
public record DepartmentResponse(
        Long id,
        Long campusId,
        String code,
        String name,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static DepartmentResponse from(Department e) {
        return new DepartmentResponse(
                e.getId(),
                e.getCampusId(),
                e.getCode(),
                e.getName(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
