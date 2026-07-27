package com.perimity.gatepass.dto.request;

import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
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

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Schema(example = "+919876543210")
    private String visitorPhone;

    @NotBlank(message = "Purpose of visit is required")
    @Size(min = 5, max = 500, message = "Describe the purpose of the visit in at least 5 characters")
    @Schema(example = "Meeting the project guide for thesis review")
    private String purpose;

    @NotNull(message = "Host is required")
    @Positive(message = "Host user id must be a positive number")
    @Schema(description = "Faculty or staff member being visited", example = "42")
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
