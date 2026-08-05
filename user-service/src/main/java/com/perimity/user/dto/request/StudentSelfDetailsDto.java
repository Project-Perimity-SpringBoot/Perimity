package com.perimity.user.dto.request;

import com.perimity.user.entity.enums.Gender;
import com.perimity.user.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/user/students/me/details - the student filling in their own
 * particulars before asking faculty to check them.
 *
 * ==========================================================================
 * NOTE WHAT IS NOT HERE
 * ==========================================================================
 * userId, campusId, departmentId, rollNo, verificationStatus, verifiedBy.
 *
 * A student edits their own facts and nothing else. campusId and departmentId
 * are staff decisions; rollNo is issued, not chosen; and a student who could
 * set verificationStatus could mark themselves VERIFIED, which would empty the
 * word of meaning. All of them come from the token or from the existing row.
 *
 * The email is not here either - it is the account's identity in auth-service
 * and cannot be changed from a profile screen. The response echoes it so the
 * student can see which address they are attached to.
 *
 * ==========================================================================
 * WHY THE PHONE IS TWO FIELDS
 * ==========================================================================
 * Country code and subscriber number are separate because a single
 * "+919876543210" cannot be validated without first guessing where the code
 * ends - and the guess is wrong for the countries with 1, 3 and 4 digit codes.
 * Split, each half has a rule that actually holds.
 *
 * The 10-digit expectation for India is enforced in the cross-field check
 * below rather than in the pattern, because it is true of +91 and not of the
 * rest of the world. A product that calls itself campus-agnostic cannot bake a
 * single country's phone length into a column.
 */
@Schema(description = "A student's own details, pending faculty verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSelfDetailsDto {

    @NotBlank(message = "First name is required")
    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Schema(example = "Anjali")
    private String firstName;

    /** Genuinely absent for many people. Never required. */
    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Schema(nullable = true, example = "Sunil")
    private String middleName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Schema(example = "Rao")
    private String lastName;

    /**
     * @Past rejects today and the future. The lower bound is the cross-field
     * check below - @Past alone would accept a birth date in 1850.
     */
    @NotNull(message = "Date of birth is required")
    @Past(message = "A date of birth must be in the past")
    @Schema(example = "2004-08-19")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    @Schema(example = "FEMALE")
    private Gender gender;

    @NotBlank(message = "Address is required")
    @Size(max = 250, message = "Keep the address under 250 characters")
    private String address;

    @NotBlank(message = "Country code is required")
    @Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must look like +91")
    @Schema(defaultValue = "+91", example = "+91")
    private String phoneCountryCode;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{4,15}$",
             message = "Enter the number without the country code, digits only")
    @Schema(example = "9876543210")
    private String phoneNumber;

    /* ---- the optional second number. Both halves or neither. ---- */

    @Pattern(regexp = "^$|^\\+[1-9][0-9]{0,3}$", message = "Country code must look like +91")
    @Schema(nullable = true, defaultValue = "+91")
    private String altPhoneCountryCode;

    @Pattern(regexp = "^$|^[0-9]{4,15}$",
             message = "Enter the number without the country code, digits only")
    @Schema(nullable = true)
    private String altPhoneNumber;

    /**
     * An Indian mobile number is exactly 10 digits and starts 6-9. Checked only
     * when the code is +91, so every other country keeps the permissive 4-15
     * rule above.
     *
     * This is the one place a country-specific rule is acceptable, because it
     * is conditional on the country rather than applied to everyone.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "An Indian mobile number is 10 digits and starts with 6, 7, 8 or 9")
    public boolean isPrimaryPhoneValidForCountry() {
        return isValidForCountry(phoneCountryCode, phoneNumber);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "An Indian mobile number is 10 digits and starts with 6, 7, 8 or 9")
    public boolean isAltPhoneValidForCountry() {
        if (altPhoneNumber == null || altPhoneNumber.isBlank()) {
            return true;
        }
        return isValidForCountry(altPhoneCountryCode, altPhoneNumber);
    }

    /**
     * A second number is a code AND a number, or neither. A number with no code
     * cannot be dialled, and a code with no number is not a contact.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Give both a country code and a number for the second phone, or leave both empty")
    public boolean isAltPhoneComplete() {
        boolean hasCode = altPhoneCountryCode != null && !altPhoneCountryCode.isBlank();
        boolean hasNumber = altPhoneNumber != null && !altPhoneNumber.isBlank();
        return hasCode == hasNumber;
    }

    /**
     * Nobody at a campus was born before 1900 or in the last ten years. Loose on
     * purpose - this catches a mistyped century, not an implausible age.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Check the date of birth - that year looks wrong")
    public boolean isDateOfBirthPlausible() {
        if (dateOfBirth == null) {
            return true;
        }
        return dateOfBirth.isAfter(LocalDate.of(1900, 1, 1))
                && dateOfBirth.isBefore(LocalDate.now().minusYears(10));
    }

    private static boolean isValidForCountry(String code, String number) {
        if (number == null || number.isBlank()) {
            return true;
        }
        if (!"+91".equals(code)) {
            return true;
        }
        return number.matches("^[6-9][0-9]{9}$");
    }
}
