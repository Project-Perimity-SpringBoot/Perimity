package com.perimity.user.dto.response;

import com.perimity.user.entity.StudentProfile;
import java.time.LocalDateTime;

/**
 * Read model for a student profile.
 *
 * NOTE the government id is returned MASKED, never in full. A profile is read
 * by faculty, by guards and by the React shell; none of them need the digits,
 * and an API that hands out full government ids in every list response is a
 * data breach waiting for someone to open the browser network tab.
 *
 * Also note there is no semester field, and there must never be one.
 */
public record StudentProfileResponse(
        Long id,
        Long userId,
        Long campusId,
        Long departmentId,
        String departmentName,
        String rollNo,
        String govIdMasked,
        boolean govIdPresent,
        String address,
        String photoS3Key,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentProfileResponse from(StudentProfile e) {
        return from(e, null);
    }

    public static StudentProfileResponse from(StudentProfile e, String departmentName) {
        return new StudentProfileResponse(
                e.getId(),
                e.getUserId(),
                e.getCampusId(),
                e.getDepartmentId(),
                departmentName,
                e.getRollNo(),
                mask(e.getGovId()),
                e.getGovId() != null && !e.getGovId().isBlank(),
                e.getAddress(),
                e.getPhotoS3Key(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    /** Shows only the last four characters: 123456789012 becomes ********9012. */
    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
