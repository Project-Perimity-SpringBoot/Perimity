package com.perimity.campus.entity;

import com.perimity.campus.entity.enums.ConfigValueType;
import com.perimity.campus.validation.ValidationPatterns;
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
 * A single per-campus setting, stored as a key-value pair so each campus can
 * carry its own rules - "is approval required", "is re-entry allowed" - without
 * any schema change. The value is always text; value_type says how to read it.
 *
 * Unique by key within a campus: one campus has exactly one value per key.
 */
@Entity
@Table(
        name = "campus_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_config_campus_key", columnNames = {"campus_id", "config_key"}),
        indexes = @Index(name = "idx_config_campus", columnList = "campus_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.CONFIG_KEY, message = ValidationPatterns.CONFIG_KEY_MESSAGE)
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /** Always stored as text; value_type tells the reader how to interpret it. */
    @Size(max = 2000)
    @Column(name = "config_value", length = 2000)
    private String configValue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    @Builder.Default
    private ConfigValueType valueType = ConfigValueType.STRING;

    @Size(max = 200)
    @Column(name = "description", length = 200)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
