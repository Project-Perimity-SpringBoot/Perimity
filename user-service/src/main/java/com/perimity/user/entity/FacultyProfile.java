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
 * A faculty member's identity information. Same pattern as StudentProfile:
 * the login account is in auth-service, user_id references it by convention,
 * files live on S3 with only the key stored here.
 */
@Entity
@Table(
        name = "faculty_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_faculty_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_faculty_campus_emp",
                        columnNames = {"campus_id", "employee_id"})
        },
        indexes = {
                @Index(name = "idx_faculty_campus", columnList = "campus_id"),
                @Index(name = "idx_faculty_department", columnList = "department_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "department_id")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Column(name = "employee_id", length = 32)
    private String employeeId;

    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Column(name = "designation", length = 100)
    private String designation;

    @Size(max = 150)
    @Column(name = "qualification", length = 150)
    private String qualification;

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
