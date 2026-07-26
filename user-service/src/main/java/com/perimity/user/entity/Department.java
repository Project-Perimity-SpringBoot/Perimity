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
 * A department, seeded per campus by that campus's admin. This is NOT a
 * hard-coded list - two campuses can have completely different departments,
 * and nothing in code may assume any particular one exists.
 *
 * Unique per campus by code: the same code may repeat across different campuses.
 */
@Entity
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_departments_campus_code", columnNames = {"campus_id", "code"}),
        indexes = @Index(name = "idx_departments_campus", columnList = "campus_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which campus owns this department. Cross-service reference by convention. */
    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.DEPARTMENT_CODE,
             message = ValidationPatterns.DEPARTMENT_CODE_MESSAGE)
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

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
