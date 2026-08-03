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

    /*
     * ======================================================================
     * EVERY READ BELOW IS CAMPUS-SCOPED. THAT IS NOT DECORATION.
     * ======================================================================
     * The unscoped versions of these three - findByHolderUserId...,
     * findByPassId..., findBySessionId... - were reachable from the controller
     * with an id taken straight from the URL. Any authenticated guard could read
     * any person's movement history on any campus by changing a number.
     *
     * They are scoped at the QUERY, not filtered after the fetch, and the
     * difference matters: a post-fetch filter has already pulled another
     * tenant's documents into this process before deciding it should not have.
     *
     * Do not add an unscoped finder here. If a caller has no campus - a Super
     * Admin - the controller resolves one explicitly rather than the repository
     * offering a way round.
     */

    /** One person's entry history, within one campus. */
    Page<EntryLog> findByCampusIdAndHolderUserIdOrderByScannedAtDesc(
            Long campusId, Long holderUserId, Pageable pageable);

    List<EntryLog> findByCampusIdAndPassIdOrderByScannedAtDesc(Long campusId, Long passId);

    /** All scans logged under one guard shift. */
    List<EntryLog> findByCampusIdAndSessionIdOrderByScannedAtAsc(Long campusId, String sessionId);

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
     *
     * Campus-scoped like every other read. An event id is a number a faculty
     * member can change in a URL, and attendance is the one entry-log path
     * FACULTY may reach - so without the campus term, a lecturer could count
     * the attendees of another campus's event.
     */
    @Query(value = "{ 'campusId': ?0, 'attributedEventId': ?1, "
                 + "'scanResult': { $ne: 'DENIED' }, 'scanDate': ?2 }",
           fields = "{ 'holderUserId': 1 }")
    List<EntryLog> findAttendeeIdsForEventDay(Long campusId, Long attributedEventId, String scanDate);

    /** Denied attempts only - the security view. */
    Page<EntryLog> findByCampusIdAndScanResultOrderByScannedAtDesc(
            Long campusId, ScanResult scanResult, Pageable pageable);
}