package com.perimity.gatepass.dto.request;

import com.perimity.gatepass.entity.enums.Gender;
import com.perimity.gatepass.entity.enums.IdType;
import com.perimity.gatepass.entity.enums.PurposeType;
import com.perimity.gatepass.entity.enums.VisitorType;
import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/visitor-requests
 *
 * Mirrors the VisitorRequest entity, minus every field the client must not set:
 * status, otpVerified, reviewedBy, reviewedAt, rejectReason and the timestamps
 * are all decided by the service layer.
 */
@Schema(description = "A visitor's registration form, submitted after email OTP verification")
@ValidDateRange(from = "visitFrom", to = "visitTo",
        message = "The last day of the visit cannot be before the first day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorRequestCreateDto {

    /**
     * The campus being visited. CLIENT-CHOSEN, and that is a reversal.
     *
     * Server-owned from Day 7 until this change, taken from the JWT.
     *
     * It used to be @JsonIgnore and overwritten from the token, on the grounds
     * that "taking it from the body would let anyone request entry to any
     * institution". Requesting is not entering. A request against a campus is
     * inert until a faculty member OF THAT CAMPUS approves it, and the approval
     * queue is campus-scoped from the token - see VisitorRequestController.
     * Approval is the gate; this field is only which queue to join.
     *
     * What the old design actually prevented was a visitor applying anywhere
     * except the campus their account was pinned to at registration - which is
     * campus 1 for everyone, because the frontend hardcodes
     * VITE_DEFAULT_CAMPUS_ID. That is the behaviour being fixed.
     *
     * The controller still checks the campus exists before the request is
     * stored. It cannot yet check the campus is ACTIVE: CampusView carries only
     * id, code and name. The picker lists active campuses only, so this is a
     * UI-side guarantee, not a server-side one. Worth closing.
     */
    @NotNull(message = "Choose the campus you are visiting")
    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "The campus being visited. Chosen by the visitor.", example = "1")
    private Long campusId;

    @NotBlank(message = "Visitor name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Schema(example = "Anita Deshmukh")
    private String visitorName;

    @NotBlank(message = "Email is required")
    @Email(message = ValidationPatterns.EMAIL_MESSAGE)
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(example = "anita.deshmukh@example.com")
    private String visitorEmail;

    @Pattern(regexp = ValidationPatterns.PHONE_IN, message = ValidationPatterns.PHONE_IN_MESSAGE)
    @Schema(example = "9876543210")
    private String visitorPhone;

    /**
     * Free-text detail. OPTIONAL since purposeType arrived.
     *
     * Requiring both means asking a visitor to write five words that repeat the
     * dropdown they just used. The category is what the queue groups by; this
     * is the sentence an approver actually reads, when there is one.
     */
    @Size(max = 500, message = "Keep the description under 500 characters")
    @Schema(example = "Meeting the project guide for thesis review")
    private String purpose;

    @NotNull(message = "Choose the purpose of your visit")
    @Schema(example = "MEETING")
    private PurposeType purposeType;

    @NotNull(message = "Choose the type of visitor you are")
    @Schema(example = "GUEST")
    private VisitorType visitorType;

    /** Optional, deliberately. PREFER_NOT_TO_SAY is also a valid answer. */
    @Schema(example = "PREFER_NOT_TO_SAY")
    private Gender gender;

    /**
     * Date of birth, not age - an age is wrong the day after it is submitted.
     *
     * @Past rejects today and the future. A visitor born today is not a visitor.
     */
    @Past(message = "Date of birth must be in the past")
    @Schema(example = "1998-04-12")
    private LocalDate dateOfBirth;

    @Schema(example = "PASSPORT")
    private IdType idType;

    /**
     * The identity document number.
     *
     * Validated for SHAPE only - letters, digits and hyphens, 4 to 40. Not
     * per-type: a checksum-accurate Aadhaar regex here would reject a valid
     * passport, and per-type validation belongs with whoever verifies the
     * document at the gate, not with a form.
     *
     * See VisitorRequest.idNumber: this can hold a full Aadhaar today, and it
     * should not. Flagged for the team, not decided here.
     */
    @Size(max = 40)
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{4,40}$",
            message = "An ID number uses letters, digits and hyphens, 4 to 40 characters")
    @Schema(example = "X1234567")
    private String idNumber;

    /**
     * Optional since the campus-queue change.
     *
     * A visitor picks a CAMPUS, not a person - they rarely know which faculty
     * member to name, and naming the wrong one used to park the request in an
     * inbox nobody was watching. Any faculty of the chosen campus can now
     * verify it, and whoever does is recorded in reviewedBy, so the audit trail
     * still names a real approver.
     *
     * Still accepted when present: a visitor who was invited by a specific host
     * can say so, and the request carries that hint into the queue. It is no
     * longer a requirement for the request to be actionable.
     *
     * @Positive still applies when supplied - Bean Validation skips nulls.
     */
    @Positive(message = "Host user id must be a positive number")
    @Schema(description = "Optional. The specific person being visited, if known.",
            example = "42")
    private Long hostUserId;

    @Positive(message = "Event id must be a positive number")
    @Schema(description = "Set only when this request belongs to an event batch", nullable = true)
    private Long eventId;

    @NotNull(message = "First day of the visit is required")
    @FutureOrPresent(message = "The visit cannot start in the past")
    @Schema(example = "2026-08-10")
    private LocalDate visitFrom;

    @NotNull(message = "Last day of the visit is required")
    @Schema(example = "2026-08-12")
    private LocalDate visitTo;
}
