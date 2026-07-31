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

    // ------------------------------------------------------- Day 10, bulk

    /**
     * Everyone who already holds a live pass for this event.
     *
     * ONE QUERY FOR THE WHOLE SHEET, and that is the entire point of it.
     *
     * The obvious way to write the "already has a pass for this event" check
     * is to call existsByHolderUserIdAndEventIdAndStatusNot inside the
     * validation loop. For a 600-row sheet that is 600 round trips, and it
     * turns a two-second validation - which a faculty member is sitting there
     * waiting for - into a two-minute one.
     *
     * This is the classic N+1 and it is very easy to reintroduce by
     * "simplifying" BulkValidationService. Do not.
     *
     * REVOKED is excluded: a revoked pass is dead and must not stop the same
     * person being issued a fresh one.
     */
    @Query("""
           SELECT DISTINCT p.holderUserId FROM GatePass p
            WHERE p.eventId = :eventId
              AND p.status <> :excluded
           """)
    List<Long> findHolderUserIdsWithLivePassForEvent(@Param("eventId") Long eventId,
                                                     @Param("excluded") PassStatus excluded);

    /**
     * Same bridge-method style as findExpiredPasses above, so the enum
     * constant stays in Java rather than being written into JPQL, where it
     * would need fully-qualifying and would silently rot if the enum moved.
     */
    default List<Long> findHolderUserIdsWithLivePassForEvent(Long eventId) {
        return findHolderUserIdsWithLivePassForEvent(eventId, PassStatus.REVOKED);
    }

    /** Drives the progress bar: how many of this batch's passes are live yet. */
    long countByBatchIdAndStatus(Long batchId, PassStatus status);

    /**
     * Every pass from one batch in a given state. The retry-failed-rows path
     * asks for PENDING - those are the rows whose generation never came back.
     */
    List<GatePass> findByBatchIdAndStatus(Long batchId, PassStatus status);

    // ------------------------------------------------- Day 12, attendance

    /**
     * Pass counts for one event, grouped by status, in ONE query.
     *
     * The alternative is five countByEventIdAndStatus calls. Same answer, five
     * round trips, and the five results are not a consistent snapshot - a pass
     * activating between call two and call four is counted twice or not at all.
     */
    @Query("""
           SELECT p.status, COUNT(p) FROM GatePass p
            WHERE p.eventId = :eventId
            GROUP BY p.status
           """)
    List<Object[]> countByEventGroupedByStatus(@Param("eventId") Long eventId);

    /**
     * The attendee roster for the CSV export, ordered by name.
     *
     * Ordered in the DATABASE rather than in Java because the export streams
     * and must not hold 600 rows in memory just to sort them. It also makes
     * the CSV stable between exports, which matters when an organiser diffs
     * two of them.
     */
    List<GatePass> findByEventIdOrderByHolderNameAsc(Long eventId);
}
