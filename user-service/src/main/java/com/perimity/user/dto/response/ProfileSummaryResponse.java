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
 *
 * ====================================================================
 *  photoS3Key vs photoUrl - two fields, because they are two things
 * ====================================================================
 *
 * photoS3Key is durable. It goes in a database, survives a bucket move and
 * means the same thing in a year.
 *
 * photoUrl is a short-lived signed link, minted fresh on every read and never
 * stored anywhere. It exists because a KEY IS NOT DISPLAYABLE. guard-service
 * needs to put a face on the scanner screen so the guard can check it against
 * the person standing there, and it holds an internal API key rather than a
 * user token - so it cannot reach the JWT-guarded /photo-url endpoint. Without
 * this field there is no path from a scan to a picture at all.
 *
 * ADDING FIELDS HERE IS SAFE. REMOVING OR RENAMING THEM IS NOT.
 * gatepass-service's InternalServiceClient.ProfileView reads userId,
 * identifierCode and photoS3Key. Jackson drops anything it does not know, so
 * new fields cost existing callers nothing - but rename one of those three and
 * that call starts returning nulls with nothing in any log to explain it.
 *
 * There is deliberately NO name field. A person's name lives in auth-service,
 * and gatepass already copies it onto the pass at issue time as holderName.
 * Duplicating it here would create a second copy that can disagree with the
 * first, and the gate would then have two answers to "who is this".
 */
public record ProfileSummaryResponse(
        Long userId,
        Long campusId,
        ProfileType profileType,
        String identifierCode,
        Long departmentId,
        String photoS3Key,
        String photoUrl
) {

    public static ProfileSummaryResponse from(StudentProfile e) {
        return from(e, null);
    }

    public static ProfileSummaryResponse from(StudentProfile e, String photoUrl) {
        return new ProfileSummaryResponse(
                e.getUserId(),
                e.getCampusId(),
                ProfileType.STUDENT,
                e.getRollNo(),
                e.getDepartmentId(),
                e.getPhotoS3Key(),
                photoUrl
        );
    }

    public static ProfileSummaryResponse from(FacultyProfile e) {
        return from(e, null);
    }

    public static ProfileSummaryResponse from(FacultyProfile e, String photoUrl) {
        return new ProfileSummaryResponse(
                e.getUserId(),
                e.getCampusId(),
                ProfileType.FACULTY,
                e.getEmployeeId(),
                e.getDepartmentId(),
                e.getPhotoS3Key(),
                photoUrl
        );
    }
}
