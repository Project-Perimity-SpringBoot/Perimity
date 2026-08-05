package com.perimity.user.entity;

import com.perimity.user.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A student's identity information. The login account itself lives in
 * auth-service; user_id is a cross-service reference to that account, matched
 * by convention, never a database foreign key across service boundaries.
 *
 * Deliberately NO semester field. The SRS is explicit: semester is not needed
 * for access control and must never appear in any form. Do not add it.
 *
 * The photo and any documents live on S3; only the key is stored here.
 */
@Entity
@Table(
        name = "student_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_student_campus_roll",
                        columnNames = {"campus_id", "roll_no"})
        },
        indexes = {
                @Index(name = "idx_student_campus", columnList = "campus_id"),
                @Index(name = "idx_student_department", columnList = "department_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The auth-service account this profile belongs to. One profile per account. */
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    /** Points at a Department row; the department list is per-campus seeded data. */
    @Column(name = "department_id")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Column(name = "roll_no", length = 32)
    private String rollNo;

    /**
     * Government id (e.g. Aadhaar) stored as a value here for now; when the
     * document-vault work lands it moves to an encrypted store. Shape only:
     * 12 digits, no country assumption beyond length.
     */
    @Pattern(regexp = "^$|^\\d{12}$", message = "Government ID must be 12 digits")
    @Column(name = "gov_id", length = 12)
    private String govId;

    @Size(max = 250)
    @Column(name = "address", length = 250)
    private String address;

    /** S3 key only, never the image bytes. Blocks path traversal via the regex. */
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Column(name = "photo_s3_key", length = 512)
    private String photoS3Key;

    /* ==================================================================
     * SELF-DECLARED DETAILS
     *
     * Everything below is entered by the STUDENT, not by staff, and is the
     * reason the verification block after it exists: none of it has been
     * checked by anyone until faculty say so.
     *
     * NOTE ON NAMES. auth-service's User.name remains the authoritative name -
     * it is what a pass carries and what an entry log records. These three
     * fields are additional detail for verification, NOT a replacement. They
     * can legitimately differ: "Anjali S. Rao" on the account, "Anjali",
     * "Sunil", "Rao" here. Do not build a pass or a gate check off these.
     * ================================================================== */

    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Column(name = "first_name", length = 60)
    private String firstName;

    /** Optional in most of the world, and genuinely absent for many people. */
    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Column(name = "middle_name", length = 60)
    private String middleName;

    @Size(max = 60)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME_PART,
             message = ValidationPatterns.PERSON_NAME_PART_MESSAGE)
    @Column(name = "last_name", length = 60)
    private String lastName;

    /**
     * LocalDate, not LocalDateTime. A birth date has no time and no zone, and
     * storing one invites a timezone shift that moves somebody's birthday by a
     * day. Range is checked in the DTO, not here.
     */
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "gender", length = 20)
    private com.perimity.user.entity.enums.Gender gender;

    /*
     * Phone is split into country code and subscriber number rather than stored
     * as one string. Two reasons: the UI needs to default the code to India
     * while letting it change, and a single "+919876543210" blob makes it
     * impossible to validate the national part without first guessing where the
     * code ends. Stored separately, joined only for display.
     */
    @Pattern(regexp = "^$|^\\+[1-9][0-9]{0,3}$",
             message = "Country code must look like +91")
    @Column(name = "phone_country_code", length = 5)
    private String phoneCountryCode;

    @Pattern(regexp = "^$|^[0-9]{4,15}$",
             message = "Phone number must be digits only, without the country code")
    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Pattern(regexp = "^$|^\\+[1-9][0-9]{0,3}$",
             message = "Country code must look like +91")
    @Column(name = "alt_phone_country_code", length = 5)
    private String altPhoneCountryCode;

    @Pattern(regexp = "^$|^[0-9]{4,15}$",
             message = "Phone number must be digits only, without the country code")
    @Column(name = "alt_phone_number", length = 15)
    private String altPhoneNumber;

    /* ==================================================================
     * VERIFICATION
     *
     * Same trio as Document (status, who, when) plus remarks, because a
     * refusal a student cannot read is a refusal they cannot act on.
     *
     * Defaulted to DRAFT rather than null: a profile that has never been
     * submitted is in a known state, not an unknown one. Null here would be
     * the same mistake as authStore treating "no token" as "unknown".
     * ================================================================== */

    /*
     * NULLABLE IN THE DATABASE, never null on a row this code writes.
     *
     * This started as @NotNull + nullable = false, which is what the column
     * deserves and which does not work. ddl-auto=update issued:
     *
     *     alter table student_profiles
     *       add column verification_status varchar(20) not null
     *
     * and Postgres refused it, because the table already had student rows and
     * every one of them would need a value the statement does not supply. The
     * column was therefore NEVER CREATED, and because Hibernate only logs that
     * failure as a warning and starts anyway, the service came up healthy and
     * then failed every single read of this table with "column does not exist".
     * A green /ping and a completely broken entity at the same time.
     *
     * Dropping nullable = false lets the column be added cleanly. @Builder.Default
     * means every row this service writes still gets DRAFT, and the readers
     * normalise a null to DRAFT anyway - see StudentProfileService.status() and
     * StudentProfileResponse.from(). Never verified is exactly what a
     * pre-existing row is.
     *
     * Backfill the old rows and the database constraint can come back:
     *   update student_profiles set verification_status = 'DRAFT'
     *     where verification_status is null;
     *   alter table student_profiles alter column verification_status set not null;
     *
     * The real fix is Flyway, which qr-service already uses (see its
     * db/migration). Until user-service has it, a new NOT NULL column on a
     * populated table must carry a default or be added in two steps.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    @Builder.Default
    private com.perimity.user.entity.enums.ProfileVerificationStatus verificationStatus =
            com.perimity.user.entity.enums.ProfileVerificationStatus.DRAFT;

    /** When the student last asked to be checked. Null until the first submit. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** The faculty user id that decided. Cross-service reference, like userId. */
    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** Mandatory on a refusal, optional on an acceptance. Shown to the student. */
    @Size(max = 500)
    @Column(name = "verification_remarks", length = 500)
    private String verificationRemarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
