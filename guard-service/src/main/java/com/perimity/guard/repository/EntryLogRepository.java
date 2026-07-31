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
     *
     * `$ne: DENIED` rather than `== ALLOWED`, and the difference is not academic.
     * An AMBER scan is an entry - the person walked in - and someone's ONLY entry
     * of a day can be amber: refused at 9am while the pass was still PENDING,
     * admitted at 10am once activated. That second scan sees an earlier log for
     * the day and comes back amber, so filtering on ALLOWED would drop a genuine
     * attendee from the organiser's count with nothing to show it happened.
     *
     * Written as "not denied" so any future permitting result is counted without
     * anyone having to remember this query exists.
     */
    @Query(value = "{ 'attributedEventId': ?0, 'scanResult': { $ne: 'DENIED' }, 'scanDate': ?1 }",
           fields = "{ 'holderUserId': 1 }")
    List<EntryLog> findAttendeeIdsForEventDay(Long attributedEventId, String scanDate);

    /** Denied attempts only - the security view. */
    Page<EntryLog> findByCampusIdAndScanResultOrderByScannedAtDesc(
            Long campusId, ScanResult scanResult, Pageable pageable);
}