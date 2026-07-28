package com.perimity.guard.repository;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.ScanResult;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EntryLogRepository extends MongoRepository<EntryLog, String> {

    /** Screen 14 - Guard Log for today, newest first. */
    Page<EntryLog> findByCampusIdAndScannedAtBetweenOrderByScannedAtDesc(
            Long campusId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<EntryLog> findByCampusIdOrderByScannedAtDesc(Long campusId, Pageable pageable);

    /** Every entry attributed to an event - feeds the attendance dashboard. */
    List<EntryLog> findByEventIdAndScannedAtBetween(Long eventId, LocalDateTime from, LocalDateTime to);

    List<EntryLog> findByEventId(Long eventId);

    /** One person's entry history. */
    Page<EntryLog> findByHolderUserIdOrderByScannedAtDesc(Long holderUserId, Pageable pageable);

    List<EntryLog> findByPassIdOrderByScannedAtDesc(Long passId);

    /** Has this person already entered today? Drives the repeat-entry AMBER rule. */
    boolean existsByHolderUserIdAndCampusIdAndScannedAtBetween(
            Long holderUserId, Long campusId, LocalDateTime from, LocalDateTime to);

    long countByCampusIdAndResultAndScannedAtBetween(
            Long campusId, ScanResult result, LocalDateTime from, LocalDateTime to);

    /**
     * Distinct attendees of an event on one day. Counting documents would
     * overcount, because a person may enter several times in a day.
     */
    @Query(value = "{ 'eventId': ?0, 'result': { $ne: 'RED' }, 'scannedAt': { $gte: ?1, $lt: ?2 } }",
           fields = "{ 'holderUserId': 1 }")
    List<EntryLog> findAttendeeIdsForEventDay(Long eventId, LocalDateTime dayStart, LocalDateTime dayEnd);

    /** Denied attempts only - the security view. */
    Page<EntryLog> findByCampusIdAndResultOrderByScannedAtDesc(
            Long campusId, ScanResult result, Pageable pageable);
}
