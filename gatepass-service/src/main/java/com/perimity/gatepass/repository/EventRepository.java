package com.perimity.gatepass.repository;

import com.perimity.gatepass.entity.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Screen 11 - Event Management list, scoped to one campus. */
    Page<Event> findByCampusIdOrderByValidFromDesc(Long campusId, Pageable pageable);

    Optional<Event> findByIdAndCampusId(Long id, Long campusId);

    /** Every event live on a given day. Drives Behavior 2 and the attendance view. */
    @Query("""
            SELECT e FROM Event e
            WHERE e.campusId = :campusId
              AND e.cancelled = false
              AND e.validFrom <= :today
              AND e.validTo >= :today
            ORDER BY e.validFrom ASC
            """)
    List<Event> findRunningEvents(@Param("campusId") Long campusId,
                                  @Param("today") LocalDate today);

    boolean existsByCampusIdAndNameIgnoreCase(Long campusId, String name);
}
