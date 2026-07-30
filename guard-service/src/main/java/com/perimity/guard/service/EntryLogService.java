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
 */
@Service
public class EntryLogService {

    private static final DateTimeFormatter SCAN_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EntryLogRepository repository;

    public EntryLogService(EntryLogRepository repository) {
        this.repository = repository;
    }

    /** The searchable register. The 90-day cap is enforced by the filter DTO. */
    public PageResponse<EntryLogResponse> search(EntryLogFilterDto filter, Pageable pageable) {
        Page<EntryLog> page = filter.getScanResult() == null
                ? repository.findByCampusIdAndScannedAtBetweenOrderByScannedAtDesc(
                        filter.getCampusId(), filter.getFrom(), filter.getTo(), pageable)
                : repository.findByCampusIdAndScanResultOrderByScannedAtDesc(
                        filter.getCampusId(), filter.getScanResult(), pageable);

        return PageResponse.from(page, EntryLogResponse::from);
    }

    /** One person's movement history. */
    public PageResponse<EntryLogResponse> byHolder(Long holderUserId, Pageable pageable) {
        return PageResponse.from(
                repository.findByHolderUserIdOrderByScannedAtDesc(holderUserId, pageable),
                EntryLogResponse::from);
    }

    /** Every scan of one pass - including the refusals, which are the interesting ones. */
    public List<EntryLogResponse> byPass(Long passId) {
        return repository.findByPassIdOrderByScannedAtDesc(passId)
                .stream().map(EntryLogResponse::from).toList();
    }

    /** Everything one guard scanned during one shift, in order. The handover view. */
    public List<EntryLogResponse> bySession(String sessionId) {
        return repository.findBySessionIdOrderByScannedAtAsc(sessionId)
                .stream().map(EntryLogResponse::from).toList();
    }

    public EntryStatsResponse stats(EntryLogFilterDto filter) {
        long allowed = repository.countByCampusIdAndScanResultAndScannedAtBetween(
                filter.getCampusId(), ScanResult.ALLOWED, filter.getFrom(), filter.getTo());
        long denied = repository.countByCampusIdAndScanResultAndScannedAtBetween(
                filter.getCampusId(), ScanResult.DENIED, filter.getFrom(), filter.getTo());
        return EntryStatsResponse.of(filter.getCampusId(), filter.getFrom(), filter.getTo(),
                allowed, denied);
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
    public EventAttendanceResponse attendance(Long eventId, String eventName,
                                              LocalDate from, LocalDate to,
                                              long registeredCount) {

        List<EventAttendanceResponse.DayAttendance> days = new ArrayList<>();
        Set<Long> uniqueOverall = new HashSet<>();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String scanDate = day.format(SCAN_DATE);

            Set<Long> attendeesToday = new HashSet<>();
            for (EntryLog log : repository.findAttendeeIdsForEventDay(eventId, scanDate)) {
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
