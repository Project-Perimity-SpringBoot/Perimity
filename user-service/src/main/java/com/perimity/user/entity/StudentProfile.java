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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
