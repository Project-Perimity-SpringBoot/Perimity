package com.perimity.gatepass.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A programme with a date range. Every pass issued for it is an EVENT pass.
 * Cancelling an event revokes all of its passes.
 */
@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "idx_ev_campus_dates", columnList = "campus_id, valid_from, valid_to"),
                @Index(name = "idx_ev_created_by", columnList = "created_by")
        }
)
@ValidDateRange(from = "validFrom", to = "validTo",
        message = "The event end date cannot be before the start date")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotBlank
    @Size(min = 3, max = 180)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @NotNull
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @NotNull
    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    /** Faculty or Campus Admin who created it. */
    @NotNull
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_cancelled", nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Used by Behavior 2 - is this event running on the day of the scan? */
    public boolean isRunningOn(LocalDate day) {
        return !cancelled && !day.isBefore(validFrom) && !day.isAfter(validTo);
    }
}
