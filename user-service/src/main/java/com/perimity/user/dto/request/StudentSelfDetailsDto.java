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

    /**
     * @NotBlank alone is the same rule as "not empty", so the single word
     * "address" passed validation - and did, in testing. This is a field
     * FACULTY VERIFY, and attesting to a one-word address attests to nothing.
     *
     * The minimum length and the digit-or-comma check below are the two cheap
     * signals that separate a real address from a placeholder: a house number,
     * a PIN code, or a separator between street and city.
     *
     * Deliberately NOT a format rule. Addresses differ far too much between
     * countries for a regex to be honest, and this product is campus-agnostic.
     *
     * Mirrors studentSelfDetailsSchema on the client exactly. A stricter client
     * rejects what the server would accept; a looser one produces a 400 the
     * user cannot act on.
     */
    @NotBlank(message = "Address is required")
    @Size(min = 10, max = 250,
          message = "Give the full address - house or street, area and city")
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
     * A second number identical to the first is not a second contact.
     *
     * Compared WITH the country code, so +91 9876543214 and +44 9876543214 are
     * correctly treated as different numbers rather than the same one.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "The second number must be different from the first")
    public boolean isAltPhoneDistinct() {
        if (altPhoneNumber == null || altPhoneNumber.isBlank()) {
            return true;
        }
        String primary = safe(phoneCountryCode) + safe(phoneNumber);
        String alternate = safe(altPhoneCountryCode) + safe(altPhoneNumber);
        return !primary.equals(alternate);
    }

    /**
     * The address must carry at least one digit or comma. Separate from the
     * @Size rule above so the two failures produce different messages - "too
     * short" and "looks like a placeholder" are different problems.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Include a house or street number, or separate the parts with commas")
    public boolean isAddressSpecific() {
        if (address == null || address.isBlank()) {
            return true;   // @NotBlank already reports this
        }
        return address.chars().anyMatch(Character::isDigit) || address.contains(",");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
