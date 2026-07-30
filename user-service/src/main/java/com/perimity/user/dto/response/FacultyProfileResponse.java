package com.perimity.user.dto.response;

import com.perimity.user.entity.FacultyProfile;
import java.time.LocalDateTime;

/**
 * Read model for a faculty profile.
 *
 * departmentName is optional: pass it in when the caller has already loaded the
 * Department, otherwise it stays null rather than firing one extra query per
 * row in a paged list.
 */
public record FacultyProfileResponse(
        Long id,
        Long userId,
        Long campusId,
        Long departmentId,
        String departmentName,
        String employeeId,
        String designation,
        String qualification,
        String photoS3Key,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static FacultyProfileResponse from(FacultyProfile e) {
        return from(e, null);
    }

    public static FacultyProfileResponse from(FacultyProfile e, String departmentName) {
        return new FacultyProfileResponse(
                e.getId(),
                e.getUserId(),
                e.getCampusId(),
                e.getDepartmentId(),
                departmentName,
                e.getEmployeeId(),
                e.getDesignation(),
                e.getQualification(),
                e.getPhotoS3Key(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
