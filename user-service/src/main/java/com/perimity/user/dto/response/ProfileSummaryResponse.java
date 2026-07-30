package com.perimity.user.dto.response;

import com.perimity.user.entity.FacultyProfile;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileType;

/**
 * The small, cheap read model other services ask for.
 *
 * gatepass-service needs "who is this person and which campus are they on" when
 * issuing a pass. It does not need an address or a government id, and it must
 * never read this service's database directly. This is the shape that call
 * returns - deliberately minimal, so the cross-service contract stays narrow.
 *
 * identifierCode is the roll number for a student and the employee id for a
 * faculty member: one field, so a caller that does not care which kind of
 * person it is does not need two code paths.
 */
public record ProfileSummaryResponse(
        Long userId,
        Long campusId,
        ProfileType profileType,
        String identifierCode,
        Long departmentId,
        String photoS3Key
) {

    public static ProfileSummaryResponse from(StudentProfile e) {
        return new ProfileSummaryResponse(
                e.getUserId(),
                e.getCampusId(),
                ProfileType.STUDENT,
                e.getRollNo(),
                e.getDepartmentId(),
                e.getPhotoS3Key()
        );
    }

    public static ProfileSummaryResponse from(FacultyProfile e) {
        return new ProfileSummaryResponse(
                e.getUserId(),
                e.getCampusId(),
                ProfileType.FACULTY,
                e.getEmployeeId(),
                e.getDepartmentId(),
                e.getPhotoS3Key()
        );
    }
}
