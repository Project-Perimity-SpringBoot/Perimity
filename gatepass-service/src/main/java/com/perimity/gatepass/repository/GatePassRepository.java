package com.perimity.gatepass.repository;

import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GatePassRepository extends JpaRepository<GatePass, Long> {

    /** Screen 8 - My Pass. A holder can legitimately have a DAILY and an EVENT pass at once. */
    List<GatePass> findByHolderUserIdAndStatusOrderByCreatedAtDesc(Long holderUserId, PassStatus status);

    List<GatePass> findByHolderUserIdOrderByCreatedAtDesc(Long holderUserId);

    Optional<GatePass> findByIdAndCampusId(Long id, Long campusId);

    Optional<GatePass> findByVisitorRequestId(Long visitorRequestId);

    /** Every pass issued for one event - used when an event is cancelled. */
    List<GatePass> findByEventId(Long eventId);

    long countByEventId(Long eventId);

    /**
     * Daily scheduled sweep (FR-PASS-3). Picks up ACTIVE passes whose window has
     * closed. A NULL valid_to is a standing pass and is deliberately skipped.
     */
    @Query("""
            SELECT p FROM GatePass p
            WHERE p.status = :status
              AND p.validTo IS NOT NULL
              AND p.validTo < :today
            """)
    List<GatePass> findExpiredPasses(@Param("status") PassStatus status,
                                     @Param("today") LocalDate today);

    default List<GatePass> findExpiredPasses(LocalDate today) {
        return findExpiredPasses(PassStatus.ACTIVE, today);
    }

    /**
     * Behavior 2 - the holder scanned their DAILY QR, but do they have an event
     * running today? If so the entry is attributed to that event instead.
     * Returns the event id, or empty.
     */
    @Query("""
            SELECT p.eventId FROM GatePass p, Event e
            WHERE p.holderUserId = :holderUserId
              AND p.passType = :eventType
              AND p.status = :activeStatus
              AND p.eventId = e.id
              AND e.cancelled = false
              AND e.validFrom <= :today
              AND e.validTo >= :today
            """)
    List<Long> findRunningEventIdsForHolder(@Param("holderUserId") Long holderUserId,
                                            @Param("today") LocalDate today,
                                            @Param("eventType") PassType eventType,
                                            @Param("activeStatus") PassStatus activeStatus);

    default Optional<Long> findActiveEventIdForHolder(Long holderUserId, LocalDate today) {
        return findRunningEventIdsForHolder(holderUserId, today, PassType.EVENT, PassStatus.ACTIVE)
                .stream()
                .findFirst();
    }

    /** Guards against issuing a second event pass to the same person for the same event. */
    boolean existsByHolderUserIdAndEventIdAndStatusNot(Long holderUserId, Long eventId, PassStatus status);

    long countByCampusIdAndStatus(Long campusId, PassStatus status);
}
