package com.perimity.auth.entity;

import com.perimity.auth.validation.ValidationPatterns;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** A single-use, time-limited password reset link. The token is stored hashed. */
@Entity
@Table(
        name = "password_resets",
        uniqueConstraints = @UniqueConstraint(name = "uk_pwreset_token", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_pwreset_user", columnList = "user_id"),
                @Index(name = "idx_pwreset_expiry", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotBlank
    @Pattern(regexp = ValidationPatterns.SHA256_HEX, message = ValidationPatterns.SHA256_HEX_MESSAGE)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsableAt(LocalDateTime now) {
        return !used && expiresAt != null && now.isBefore(expiresAt);
    }
}
