package com.perimity.auth.entity;

import com.perimity.auth.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
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

/**
 * A person barred from one campus (FR-BLK-1 ... FR-BLK-6).
 *
 * Checked on every registration and every bulk-upload row. A blocked
 * registration is refused with a deliberately vague message - never tell the
 * person they are blocklisted (FR-BLK-4).
 *
 * Scoped per campus: being barred from one campus does not bar you from another.
 */
@Entity
@Table(
        name = "blocklist",
        indexes = {
                @Index(name = "idx_bl_campus_email", columnList = "campus_id, email"),
                @Index(name = "idx_bl_campus_phone", columnList = "campus_id, phone")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlocklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Email
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Column(name = "email", length = 180)
    private String email;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Column(name = "phone", length = 20)
    private String phone;

    /** Mandatory by FR-BLK-1. A blocklist entry with no reason is unauditable. */
    @NotBlank(message = "A reason is required for every blocklist entry")
    @Size(min = 5, max = 500)
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @NotNull
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Either field alone is enough, but an entry with neither blocks nobody.
     * No single-field annotation can express "at least one of these two", and
     * a custom class-level annotation would be overkill for one use.
     */
    @AssertTrue(message = "Provide an email address or a phone number to block")
    public boolean isEmailOrPhonePresent() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }
}
