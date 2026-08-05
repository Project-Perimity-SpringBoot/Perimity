package com.perimity.user.dto.response;

import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.Gender;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import java.time.LocalDate;
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
 *
 * ==========================================================================
 * TWO FACTORIES, AND WHY THE DIRECTORY GETS THE SMALLER ONE
 * ==========================================================================
 * Contact details - date of birth, phone numbers, address - are returned by
 * from(), used for reading ONE profile and for the verification queue. Faculty
 * checking a student's submitted details obviously need to see the details they
 * are being asked to check.
 *
 * forDirectory() blanks them, and the paged student directory uses that one.
 * The directory answers "who is on this campus", and no part of that question
 * needs a home address or a mobile number. Without the split, any faculty
 * account could page through the directory twenty rows at a time and walk off
 * with every student's date of birth, address and phone number - a bulk PII
 * export dressed up as a normal screen, leaving nothing in the logs that looks
 * unusual. One profile at a time is a lookup; all of them is a dump.
 *
 * NOT a guard concern, despite how it looks. Guards never receive this record
 * at all: they read ProfileSummaryResponse over the internal endpoints, and
 * CurrentUser.requireSelfOrStaff refuses them this one, since isStaff() covers
 * only admins and faculty.
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

        /* ---- self-declared; unverified until verificationStatus says otherwise ---- */
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        LocalDate dateOfBirth,
        Gender gender,
        String phoneCountryCode,
        String phoneNumber,
        String altPhoneCountryCode,
        String altPhoneNumber,

        /* ---- verification ---- */
        ProfileVerificationStatus verificationStatus,
        boolean editable,
        LocalDateTime submittedAt,
        Long verifiedBy,
        LocalDateTime verifiedAt,
        String verificationRemarks,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentProfileResponse from(StudentProfile e) {
        return from(e, null);
    }

    public static StudentProfileResponse from(StudentProfile e, String departmentName) {
        /*
         * Rows created before the verification columns existed have a null
         * status, because ddl-auto=update adds the column without backfilling
         * it. Treat those as DRAFT: never verified is exactly what they are.
         */
        ProfileVerificationStatus status = e.getVerificationStatus() == null
                ? ProfileVerificationStatus.DRAFT
                : e.getVerificationStatus();

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

                e.getFirstName(),
                e.getMiddleName(),
                e.getLastName(),
                displayName(e),
                e.getDateOfBirth(),
                e.getGender(),
                e.getPhoneCountryCode(),
                e.getPhoneNumber(),
                e.getAltPhoneCountryCode(),
                e.getAltPhoneNumber(),

                status,
                status.isStudentEditable(),
                e.getSubmittedAt(),
                e.getVerifiedBy(),
                e.getVerifiedAt(),
                e.getVerificationRemarks(),

                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    /**
     * The directory shape: identity and verification state, no contact details.
     *
     * The blanked fields are null rather than masked. A masked phone number
     * ("*****3210") tells the reader a number exists and leaks its last digits
     * for nothing; the directory has no use for either. govId stays masked
     * because govIdPresent is genuinely useful there - "has this student given
     * us an ID at all" is a real directory question in a way that "what is their
     * mobile number" is not.
     *
     * displayName is KEPT. It is the whole point of a directory, it is already
     * public within the campus, and auth-service publishes the same person's
     * name anyway - blanking it here would hide nothing and break the screen.
     */
    public static StudentProfileResponse forDirectory(StudentProfile e) {
        StudentProfileResponse full = from(e, null);
        return new StudentProfileResponse(
                full.id(),
                full.userId(),
                full.campusId(),
                full.departmentId(),
                full.departmentName(),
                full.rollNo(),
                full.govIdMasked(),
                full.govIdPresent(),
                null,               // address
                full.photoS3Key(),

                full.firstName(),
                full.middleName(),
                full.lastName(),
                full.displayName(),
                null,               // dateOfBirth
                full.gender(),
                null,               // phoneCountryCode
                null,               // phoneNumber
                null,               // altPhoneCountryCode
                null,               // altPhoneNumber

                full.verificationStatus(),
                full.editable(),
                full.submittedAt(),
                full.verifiedBy(),
                full.verifiedAt(),
                full.verificationRemarks(),

                full.createdAt(),
                full.updatedAt()
        );
    }

    /**
     * The three name parts joined for display, skipping blanks so a student
     * with no middle name does not render with a double space.
     *
     * Null when nothing has been filled in, rather than an empty string, so the
     * UI can fall back to the authoritative account name from auth-service
     * instead of showing a blank where a name should be.
     *
     * A CONVENIENCE, not an identity. Passes and entry logs still carry
     * auth-service's User.name.
     */
    private static String displayName(StudentProfile e) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, e.getFirstName());
        appendPart(sb, e.getMiddleName());
        appendPart(sb, e.getLastName());
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(part.trim());
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
