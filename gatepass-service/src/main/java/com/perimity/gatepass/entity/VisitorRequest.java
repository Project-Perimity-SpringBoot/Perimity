package com.perimity.gatepass.entity;

import com.perimity.gatepass.entity.enums.RequestStatus;
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

    @NotBlank
    @Size(min = 5, max = 500, message = "Describe the purpose of the visit in at least 5 characters")
    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    /** Faculty / staff user being visited. Lives in AuthDB - reference by id only, never a JOIN. */
    @NotNull
    @Column(name = "host_user_id", nullable = false)
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
