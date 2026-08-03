package com.perimity.guard.service;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.dto.request.EntryLogFilterDto;
import com.perimity.guard.dto.response.EntryLogResponse;
import com.perimity.guard.dto.response.EntryStatsResponse;
import com.perimity.guard.dto.response.EventAttendanceResponse;
import com.perimity.guard.dto.response.PageResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.perimity.guard.repository.EntryLogRepository;

/**
 * Reading the digital gate register.
 *
 * Everything here is paged or bounded. entry_logs is the only collection in the
 * platform that will genuinely reach millions of documents, and an unbounded
 * query over it is a demonstration that times out.
 *
 * ==========================================================================
 * EVERY METHOD TAKES campusId FIRST, AND THAT IS THE SECURITY CONTROL
 * ==========================================================================
 * It is a required parameter rather than something this class reads from the
 * security context, for two reasons.
 *
 * Forgetting it is a COMPILE ERROR. The previous design had the campus arriving
 * inside a DTO the client controlled, so an endpoint that failed to check it
 * looked exactly like one that did. Making it an argument means the next person
 * to add a read here cannot omit the scope without the build telling them.
 *
 * And this class stays free of Spring Security. Reading SecurityContextHolder
 * here would have worked equally well at runtime, but it would make every unit
 * test set up an authentication context to call a method that only reads Mongo.
 * The caller's identity is the controller's business; this class just refuses to
 * answer a question that does not name a campus.
 *
 * The campus passed in is ALWAYS derived from the verified token - see
 * EntryLogController.resolveCampus. Never from a request body.
 */
@Service
public class EntryLogService {

    private static final DateTimeFormatter SCAN_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EntryLogRepository repository;

    public EntryLogService(EntryLogRepository repository) {
        this.repository = repository;
    }

    /** The searchable register. The 90-day cap is enforced by the filter DTO. */
    public PageResponse<EntryLogResponse> search(Long campusId, EntryLogFilterDto filter,
                                                 Pageable pageable) {
        Page<EntryLog> page = filter.getScanResult() == null
                ? repository.findByCampusIdAndScannedAtBetweenOrderByScannedAtDesc(
                        campusId, filter.getFrom(), filter.getTo(), pageable)
                : repository.findByCampusIdAndScanResultOrderByScannedAtDesc(
                        campusId, filter.getScanResult(), pageable);

        return PageResponse.from(page, EntryLogResponse::from);
    }

    /** One person's movement history, within the caller's campus. */
    public PageResponse<EntryLogResponse> byHolder(Long campusId, Long holderUserId,
                                                   Pageable pageable) {
        return PageResponse.from(
                repository.findByCampusIdAndHolderUserIdOrderByScannedAtDesc(
                        campusId, holderUserId, pageable),
                EntryLogResponse::from);
    }

    /**
     * Every scan of one pass - including the refusals, which are the interesting ones.
     *
     * A pass belonging to another campus returns an empty list rather than a 404.
     * That is deliberate: distinguishing "no such pass" from "not your pass" tells
     * a prober which ids exist elsewhere, and the register is not the place to
     * leak the shape of another tenant's data.
     */
    public List<EntryLogResponse> byPass(Long campusId, Long passId) {
        return repository.findByCampusIdAndPassIdOrderByScannedAtDesc(campusId, passId)
                .stream().map(EntryLogResponse::from).toList();
    }

    /** Everything one guard scanned during one shift, in order. The handover view. */
    public List<EntryLogResponse> bySession(Long campusId, String sessionId) {
        return repository.findByCampusIdAndSessionIdOrderByScannedAtAsc(campusId, sessionId)
                .stream().map(EntryLogResponse::from).toList();
    }

    /**
     * One count per result, not two.
     *
     * Counting only ALLOWED and DENIED left AMBER scans out of both, so the
     * dashboard total disagreed with the number of documents actually in the
     * collection - and nothing on screen would have shown it.
     */
    public EntryStatsResponse stats(Long campusId, EntryLogFilterDto filter) {
        long allowed = countOf(campusId, filter, ScanResult.ALLOWED);
        long amber = countOf(campusId, filter, ScanResult.AMBER);
        long denied = countOf(campusId, filter, ScanResult.DENIED);

        return EntryStatsResponse.of(campusId, filter.getFrom(), filter.getTo(),
                allowed, amber, denied);
    }

    private long countOf(Long campusId, EntryLogFilterDto filter, ScanResult result) {
        return repository.countByCampusIdAndScanResultAndScannedAtBetween(
                campusId, result, filter.getFrom(), filter.getTo());
    }

    /**
     * The organiser attendance view.
     *
     * Counted on attributedEventId, which is what makes Behavior 2 work: a
     * student who scanned their DAILY QR during the event is still counted,
     * because the credit was decided at write time.
     *
     * De-duplicated per day by holder. Somebody stepping out for lunch and
     * coming back is one attendee, not two - but every scan is still in the log.
     *
     * registeredCount comes from the caller. That number lives in
     * gatepass-service, and reading another service's database is the one thing
     * the architecture forbids.
     */
    public EventAttendanceResponse attendance(Long campusId, Long eventId, String eventName,
                                              LocalDate from, LocalDate to,
                                              long registeredCount) {

        List<EventAttendanceResponse.DayAttendance> days = new ArrayList<>();
        Set<Long> uniqueOverall = new HashSet<>();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String scanDate = day.format(SCAN_DATE);

            Set<Long> attendeesToday = new HashSet<>();
            for (EntryLog log : repository.findAttendeeIdsForEventDay(campusId, eventId, scanDate)) {
                if (log.getHolderUserId() != null) {
                    attendeesToday.add(log.getHolderUserId());
                }
            }

            uniqueOverall.addAll(attendeesToday);
            days.add(EventAttendanceResponse.DayAttendance.of(
                    scanDate, attendeesToday.size(), registeredCount));
        }

        return EventAttendanceResponse.of(eventId, eventName, registeredCount,
                uniqueOverall.size(), days);
    }
}
