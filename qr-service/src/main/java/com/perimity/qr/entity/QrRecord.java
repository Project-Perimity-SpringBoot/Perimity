package com.perimity.qr.entity;

import com.perimity.qr.validation.ValidDateRange;
import com.perimity.qr.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
 * The validated pass token. This is what the guard's scan is checked against.
 *
 * The QR token itself is AES-256 encrypted and is NEVER stored here in plain
 * text - only its SHA-256 hash, so a database leak cannot forge a pass.
 * The PNG and the PDF live in object storage; only their keys are stored.
 */
@Entity
@Table(
        name = "qr_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_qr_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_qr_pass", columnList = "pass_id"),
                @Index(name = "idx_qr_active", columnList = "pass_id, is_active"),
                @Index(name = "idx_qr_campus", columnList = "campus_id")
        }
)
// endNullable = true: a QR mirroring a standing DAILY pass has no end date.
@ValidDateRange(from = "validFrom", to = "validTo", endNullable = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pass in GatePassDB. Cross-service reference by id only - never a JOIN. */
    @NotNull
    @Column(name = "pass_id", nullable = false)
    private Long passId;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    /**
     * SHA-256 of the encrypted token. Never the token itself.
     * The regex is a tripwire: if anything ever writes a non-hex value here,
     * validation fails loudly instead of silently storing a plain token.
     */
    @NotBlank
    @Pattern(regexp = ValidationPatterns.SHA256_HEX, message = ValidationPatterns.SHA256_HEX_MESSAGE)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Size(max = 300)
    @Column(name = "qr_key", length = 300)
    private String qrKey;

    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Size(max = 300)
    @Column(name = "pdf_key", length = 300)
    private String pdfKey;

    @NotNull
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** NULL for a standing daily pass. Nullable on purpose. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /**
     * Set to false when the pass is re-issued or revoked (v1.1 token
     * invalidation). Nothing is ever hard-deleted, so the old row stays for audit.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Size(max = 200)
    @Column(name = "invalidated_reason", length = 200)
    private String invalidatedReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Shape check only. Whether the pass is ACTIVE is gatepass-service's answer. */
    public boolean isUsableOn(LocalDate day) {
        if (!active || validFrom == null || day.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !day.isAfter(validTo);
    }
}
