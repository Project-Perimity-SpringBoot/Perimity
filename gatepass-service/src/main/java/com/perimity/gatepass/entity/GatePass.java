package com.perimity.gatepass.entity;

import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
 * The pass itself. A pass is NOT a person - one identity can hold many passes
 * over time, and can hold a DAILY and an EVENT pass simultaneously.
 *
 * The QR image and the printable PDF live in object storage. Only the keys are
 * stored here, never the files.
 */
@Entity
@Table(
        name = "gate_passes",
        indexes = {
                @Index(name = "idx_gp_holder_status", columnList = "holder_user_id, status"),
                @Index(name = "idx_gp_campus_status", columnList = "campus_id, status"),
                @Index(name = "idx_gp_event", columnList = "event_id"),
                @Index(name = "idx_gp_expiry_sweep", columnList = "status, valid_to")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatePass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identity in AuthDB. Cross-service reference by convention - no DB foreign key. */
    @NotNull
    @Column(name = "holder_user_id", nullable = false)
    private Long holderUserId;

    /**
     * Copied at issue time so the QR/PDF job and the gate scan response never
     * need a live call into auth-service. Day 7's RabbitMQ payload requires it.
     */
    @NotBlank
    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    /** Set when this pass came from an approved visitor request, otherwise null. */
    @Column(name = "visitor_request_id")
    private Long visitorRequestId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false, length = 20)
    private PassType passType;

    /** Null for DAILY passes. Set for EVENT passes. */
    @Column(name = "event_id")
    private Long eventId;

    @NotNull
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /**
     * NULL means a standing pass with no end date - the normal case for a
     * student's DAILY pass. Nullable on purpose. Do not add nullable = false.
     */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PassStatus status = PassStatus.PENDING;

    /** Mandatory when status = REVOKED (FR-PASS-5, revoke with reason). */
    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Why the pass was paused - set when a sensitive profile field changed. */
    @Column(name = "paused_reason", length = 500)
    private String pausedReason;

    /** Object storage key for the QR PNG. Written by qr-service on activation. */
    @Column(name = "qr_key", length = 300)
    private String qrKey;

    /** Object storage key for the printable PDF pass. */
    @Column(name = "pdf_key", length = 300)
    private String pdfKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** True when today falls inside the validity window. Null validTo = open ended. */
    public boolean isWithinValidityWindow(LocalDate today) {
        if (validFrom != null && today.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !today.isAfter(validTo);
    }
}
