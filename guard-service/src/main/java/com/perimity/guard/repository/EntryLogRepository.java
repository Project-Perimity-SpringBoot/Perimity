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

    /** Guard Log for today, newest first. */
    Page<EntryLog> findByCampusIdAndScannedAtBetweenOrderByScannedAtDesc(
            Long campusId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<EntryLog> findByCampusIdOrderByScannedAtDesc(Long campusId, Pageable pageable);

    /** Every entry attributed to a running event - feeds the attendance dashboard. */
    List<EntryLog> findByAttributedEventIdAndScanDate(Long attributedEventId, String scanDate);

    List<EntryLog> findByAttributedEventId(Long attributedEventId);

    /** One person's entry history. */
    Page<EntryLog> findByHolderUserIdOrderByScannedAtDesc(Long holderUserId, Pageable pageable);

    List<EntryLog> findByPassIdOrderByScannedAtDesc(Long passId);

    /** All scans logged under one guard shift. */
    List<EntryLog> findBySessionIdOrderByScannedAtAsc(String sessionId);

    /** Has this person already entered today? Drives the repeat-entry AMBER rule upstream. */
    boolean existsByHolderUserIdAndCampusIdAndScannedAtBetween(
            Long holderUserId, Long campusId, LocalDateTime from, LocalDateTime to);

    long countByCampusIdAndScanResultAndScannedAtBetween(
            Long campusId, ScanResult scanResult, LocalDateTime from, LocalDateTime to);

    /**
     * Distinct attendees of an event on one day. Counting documents would
     * overcount, because a person may enter several times in a day.
     */
    @Query(value = "{ 'attributedEventId': ?0, 'scanResult': 'ALLOWED', 'scanDate': ?1 }",
           fields = "{ 'holderUserId': 1 }")
    List<EntryLog> findAttendeeIdsForEventDay(Long attributedEventId, String scanDate);

    /** Denied attempts only - the security view. */
    Page<EntryLog> findByCampusIdAndScanResultOrderByScannedAtDesc(
            Long campusId, ScanResult scanResult, Pageable pageable);
}