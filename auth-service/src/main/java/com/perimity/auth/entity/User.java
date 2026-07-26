package com.perimity.auth.entity;

import com.perimity.auth.entity.enums.Role;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * One record per person who can log in, across every campus.
 * Email is the universal key that ties a person together everywhere in Perimity.
 *
 * Nothing is ever hard-deleted. Accounts are deactivated, not removed.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = {
                @Index(name = "idx_users_campus_role", columnList = "campus_id, role"),
                @Index(name = "idx_users_active", columnList = "is_active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @NotBlank
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * bcrypt only. NULL for a VISITOR, who never has a password.
     * The regex makes it impossible to store a plain password by accident.
     */
    @Pattern(regexp = ValidationPatterns.BCRYPT_HASH, message = ValidationPatterns.BCRYPT_HASH_MESSAGE)
    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** NULL only for SUPER_ADMIN, who is platform-wide rather than per campus. */
    @Column(name = "campus_id")
    private Long campusId;

    /** Forces a password change on first login for a seeded or admin-created account. */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Min(0)
    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    /** Set after LOGIN_MAX_FAILED_ATTEMPTS. Null when the account is not locked. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Two rules no single-field annotation can express, so they sit here.
     * A custom class-level annotation would be overkill - these rules are used
     * once, in this class, and are not reusable anywhere else.
     */
    @AssertTrue(message = "A visitor must not have a password; every other role must have one")
    public boolean isPasswordConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return role.canLoginWithPassword() ? passwordHash != null : passwordHash == null;
    }

    @AssertTrue(message = "Only a Super Admin may have no campus; every other role needs one")
    public boolean isCampusConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return role.requiresCampus() ? campusId != null : campusId == null;
    }

    /** Shape check only. The lockout window itself comes from .env. */
    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }
}
