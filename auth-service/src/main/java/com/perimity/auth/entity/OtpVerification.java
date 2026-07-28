package com.perimity.auth.entity;

import com.perimity.auth.entity.enums.OtpPurpose;
import com.perimity.auth.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One issued OTP. The code is stored as a SHA-256 hash and NEVER in plain text,
 * so a database leak cannot be replayed.
 *
 * Expires after OTP_EXPIRY_MINUTES; locked after OTP_MAX_ATTEMPTS.
 */
@Entity
@Table(
        name = "otp_verifications",
        indexes = {
                @Index(name = "idx_otp_email_purpose", columnList = "email, purpose"),
                @Index(name = "idx_otp_expiry", columnList = "expires_at"),
                @Index(name = "idx_otp_lookup", columnList = "email, purpose, consumed")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Column(name = "email", nullable = false, length = 180)
    private String email;

    /** SHA-256 hex of the six-digit code. Never the code itself. */
    @NotBlank
    @Pattern(regexp = ValidationPatterns.SHA256_HEX, message = ValidationPatterns.SHA256_HEX_MESSAGE)
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    /** Stops a login OTP being replayed against a password reset. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "campus_id")
    private Long campusId;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Min(0)
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Single use. Once true this row can never verify again. */
    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpiredAt(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now, int maxAttempts) {
        return !consumed && !isExpiredAt(now) && attempts < maxAttempts;
    }
}
