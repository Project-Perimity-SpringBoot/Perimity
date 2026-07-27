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
 * A physical gate on a campus - Main Gate, Back Gate, and so on. A guard binds
 * to exactly one gate per session, and every scan is recorded against the gate
 * it happened at. Gates are unique by name within their campus.
 */
@Entity
@Table(
        name = "campus_gates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gate_campus_name", columnNames = {"campus_id", "name"}),
        indexes = @Index(name = "idx_gate_campus", columnList = "campus_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusGate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 150)
    @Column(name = "location", length = 150)
    private String location;

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
