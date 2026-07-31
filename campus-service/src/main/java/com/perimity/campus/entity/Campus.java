package com.perimity.campus.entity;

import com.perimity.campus.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
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
 * One row per institution the platform serves. A single deployment hosts any
 * number of campuses; this table is what makes Perimity multi-tenant.
 *
 * The campus code is the stable, url-safe handle used in S3 prefixes and log
 * lines. It is unique platform-wide and never reused.
 *
 * admin_user_id points at the Campus Admin account in auth-service - a
 * cross-service reference by convention, not a database foreign key.
 */
@Entity
@Table(
        name = "campuses",
        uniqueConstraints = @UniqueConstraint(name = "uk_campus_code", columnNames = "code"),
        indexes = @Index(name = "idx_campus_active", columnList = "is_active")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.CAMPUS_CODE, message = ValidationPatterns.CAMPUS_CODE_MESSAGE)
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Size(max = 250)
    @Column(name = "address", length = 250)
    private String address;

    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Size(max = 180)
    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /** S3 key for the logo, never the image bytes. Regex blocks path traversal. */
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Column(name = "logo_s3_key", length = 512)
    private String logoS3Key;

    /** The Campus Admin account in auth-service that owns this campus. */
    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
