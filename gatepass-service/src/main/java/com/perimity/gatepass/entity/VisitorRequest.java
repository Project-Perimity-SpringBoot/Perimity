package com.perimity.gatepass.entity;

import com.perimity.gatepass.entity.enums.Gender;
import com.perimity.gatepass.entity.enums.IdType;
import com.perimity.gatepass.entity.enums.PurposeType;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.entity.enums.VisitorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A visitor's registration form, before any pass exists.
 * Approving one of these is what creates a GatePass row (status = PENDING).
 */
@Entity
@Table(
        name = "visitor_requests",
        indexes = {
                @Index(name = "idx_vr_campus_status", columnList = "campus_id, status"),
                @Index(name = "idx_vr_email", columnList = "visitor_email"),
                @Index(name = "idx_vr_host", columnList = "host_user_id")
        }
)
@ValidDateRange(from = "visitFrom", to = "visitTo",
        message = "The last day of the visit cannot be before the first day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant scope. Every campus-specific row carries this. */
    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotBlank
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Column(name = "visitor_name", nullable = false, length = 120)
    private String visitorName;

    /** The universal key across the whole system. Identities are matched by email. */
    @NotBlank
    @Email
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Column(name = "visitor_email", nullable = false, length = 180)
    private String visitorEmail;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Column(name = "visitor_phone", length = 20)
    private String visitorPhone;

    /**
     * Free-text detail. OPTIONAL since purposeType arrived - the category is
     * what the queue filters on, this is the sentence the approver reads.
     *
     * Was NOT NULL. See db/migration/V2__visitor_request_identity_fields.sql.
     */
    @Size(max = 500, message = "Keep the description under 500 characters")
    @Column(name = "purpose", length = 500)
    private String purpose;

    /** The category of visit. Required - a queue needs something to group by. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose_type", nullable = false, length = 20)
    private PurposeType purposeType;

    /** Who the visitor is, as distinct from why they are here. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visitor_type", nullable = false, length = 20)
    private VisitorType visitorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    /**
     * Date of birth, not age. An age column is wrong the day after it is
     * written; this is correct forever and the age is derived when displayed.
     */
    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", length = 20)
    private IdType idType;

    /**
     * The identity document number.
     *
     * ==================================================================
     *  STORED IN FULL TODAY. THAT SHOULD NOT SURVIVE THIS PROJECT.
     * ==================================================================
     * IdType.AADHAAR means this column can hold a full Aadhaar number, which is
     * regulated personal data in India - the Aadhaar Act restricts who may
     * store it and obliges them to protect it. A campus gate log is not a
     * lawful reason to keep one.
     *
     * What a gate actually needs is "the guard checked a document and it
     * matched": the last four digits plus the type is enough for that, and a
     * breach of four digits is not a breach of an identity. Recording the full
     * value buys nothing at the gate and creates a liability in the database.
     *
     * Left whole here because the requirement asked for the field, and
     * truncating without saying so would hide the decision. Flagged for the
     * team rather than decided unilaterally.
     */
    @Size(max = 40)
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{4,40}$",
            message = "An ID number uses letters, digits and hyphens, 4 to 40 characters")
    @Column(name = "id_number", length = 40)
    private String idNumber;

    /** Object-storage keys, not URLs. Same convention as qrKey and pdfKey. */
    @Size(max = 300)
    @Column(name = "id_proof_key", length = 300)
    private String idProofKey;

    @Size(max = 300)
    @Column(name = "photo_key", length = 300)
    private String photoKey;

    /**
     * Faculty / staff user being visited. Lives in AuthDB - reference by id
     * only, never a JOIN.
     *
     * NULLABLE since the campus-queue change. A visitor chooses a campus, not a
     * person, so most requests have no host: any faculty of the campus can
     * approve, and whoever does is recorded in reviewedBy. It stays set when a
     * visitor genuinely knows who invited them.
     *
     * Was NOT NULL, which made every request from the new form fail on insert.
     * ddl-auto=update does not drop a NOT NULL from a populated table - see
     * db/migration/V1__visitor_request_host_optional.sql.
     */
    @Column(name = "host_user_id")
    private Long hostUserId;

    /** Set when the request is part of an event batch, otherwise null. */
    @Column(name = "event_id")
    private Long eventId;

    @NotNull
    @Column(name = "visit_from", nullable = false)
    private LocalDate visitFrom;

    @NotNull
    @Column(name = "visit_to", nullable = false)
    private LocalDate visitTo;

    /** The form cannot be submitted until the visitor's email OTP has been verified. */
    @Column(name = "otp_verified", nullable = false)
    @Builder.Default
    private boolean otpVerified = false;

    /**
     * The visitor's identity in AuthDB, supplied by auth-service when it
     * confirms the email OTP.
     *
     * Null until then, and that is the point: GatePass.holderUserId is @NotNull,
     * so a pass physically cannot be issued for a request whose email was never
     * verified. The database enforces the rule rather than a comment asking
     * someone to remember it.
     */
    @Column(name = "visitor_user_id")
    private Long visitorUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Mandatory when status = REJECTED. */
    @Size(max = 500)
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
