package com.perimity.auth.entity;

import com.perimity.auth.entity.enums.AuditAction;
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
 * Append-only security trail (FR-AUD-1). Rows are written and never updated
 * or deleted - that is what makes it evidence rather than a report.
 *
 * There is deliberately no @UpdateTimestamp and no setter usage after insert.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_campus_time", columnList = "campus_id, created_at"),
                @Index(name = "idx_audit_actor", columnList = "actor_user_id"),
                @Index(name = "idx_audit_action", columnList = "action, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for an anonymous action, such as a failed login on an unknown email. */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private Role actorRole;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    /** What was acted on, e.g. "USER:42" or "GATE_PASS:118". */
    @Size(max = 120)
    @Column(name = "target_entity", length = 120)
    private String targetEntity;

    @Column(name = "campus_id")
    private Long campusId;

    @Pattern(regexp = ValidationPatterns.IP_ADDRESS, message = ValidationPatterns.IP_ADDRESS_MESSAGE)
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /** Never put a password, OTP, or token in here, not even a hash. */
    @Size(max = 1000)
    @Column(name = "details", length = 1000)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
