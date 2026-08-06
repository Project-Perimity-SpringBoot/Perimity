package com.perimity.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.dto.request.EntryLogFilterDto;
import com.perimity.guard.dto.response.EntryStatsResponse;
import com.perimity.guard.dto.response.EventAttendanceResponse;
import com.perimity.guard.repository.EntryLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reporting over the register.
 *
 * ==========================================================================
 * WHY THESE TESTS EXIST
 * ==========================================================================
 * Both bugs they cover were introduced by adding AMBER on Day 11, and both
 * survived a suite of 31 green tests - because every one of those tests asked
 * "what happens on a scan" and none asked "what do the reports say afterwards".
 *
 * A result that is neither ALLOWED nor DENIED broke two places that had quietly
 * assumed there were only two outcomes. Neither failed loudly. The dashboard
 * simply undercounted, and the organiser's attendance list simply omitted people.
 *
 * That is the failure mode worth guarding: a report that looks right and is not.
 */
@ExtendWith(MockitoExtension.class)
class EntryLogServiceTest {

    private static final Long CAMPUS_ID = 1L;
    private static final LocalDateTime FROM = LocalDateTime.now().minusDays(1);
    private static final LocalDateTime TO = LocalDateTime.now();

    @Mock private EntryLogRepository repository;
    @Mock private org.springframework.data.mongodb.core.MongoTemplate mongo;

    private EntryLogService service;

    @BeforeEach
    void setUp() {
        service = new EntryLogService(repository, mongo);
    }

    @Test
    @DisplayName("stats counts amber, and the total reconciles with the collection")
    void statsCountsAmber() {
        countReturns(ScanResult.ALLOWED, 40);
        countReturns(ScanResult.AMBER, 7);
        countReturns(ScanResult.DENIED, 3);

        EntryStatsResponse stats = service.stats(CAMPUS_ID, filter());

        assertThat(stats.allowedCount()).isEqualTo(40);
        assertThat(stats.amberCount()).isEqualTo(7);
        assertThat(stats.deniedCount()).isEqualTo(3);

        // 47 people got in. The earlier version reported 40 and lost seven
        // without anything on screen suggesting a number was missing.
        assertThat(stats.entriesPermitted()).isEqualTo(47);

        // The total must equal the number of documents actually written. If this
        // ever drifts, the dashboard is lying about the register beneath it.
        assertThat(stats.totalScans()).isEqualTo(50);
    }

    @Test
    @DisplayName("attendance counts a person once a day however many times they scan")
    void attendanceDeduplicatesPerDay() {
        LocalDate day = LocalDate.now();
        // Same person three times, plus one other. A person stepping out for
        // lunch and returning is one attendee, not three.
        when(repository.findAttendeeIdsForEventDay(eq(CAMPUS_ID), eq(17L), any()))
                .thenReturn(List.of(holder(108L), holder(108L), holder(108L), holder(204L)));

        EventAttendanceResponse attendance =
                service.attendance(CAMPUS_ID, 17L, "Annual Technical Summit", day, day, 600);

        assertThat(attendance.days()).hasSize(1);
        assertThat(attendance.days().get(0).attendedCount()).isEqualTo(2);
        assertThat(attendance.uniqueAttendeeCount()).isEqualTo(2);

        // The number an organiser actually asks about.
        assertThat(attendance.neverShowedCount()).isEqualTo(598);
    }

    @Test
    @DisplayName("a person counted on two days is one attendee overall, two attendances")
    void attendanceUniquePeopleAcrossDays() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now();
        when(repository.findAttendeeIdsForEventDay(eq(CAMPUS_ID), eq(17L), any()))
                .thenReturn(List.of(holder(108L)));

        EventAttendanceResponse attendance =
                service.attendance(CAMPUS_ID, 17L, "Annual Technical Summit", start, end, 10);

        // Two days, one attendance each...
        assertThat(attendance.days()).hasSize(2);
        assertThat(attendance.days().get(0).attendedCount()).isEqualTo(1);
        assertThat(attendance.days().get(1).attendedCount()).isEqualTo(1);

        // ...but one human being. Summing the days would say two, and an
        // organiser reading "2 of 10 attended" for one person would be wrong.
        assertThat(attendance.uniqueAttendeeCount()).isEqualTo(1);
        assertThat(attendance.neverShowedCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("no registered count does not produce a divide-by-zero percentage")
    void zeroRegisteredIsSafe() {
        LocalDate day = LocalDate.now();
        when(repository.findAttendeeIdsForEventDay(eq(CAMPUS_ID), eq(17L), any()))
                .thenReturn(List.of(holder(108L)));

        EventAttendanceResponse attendance = service.attendance(CAMPUS_ID, 17L, "Ad-hoc", day, day, 0);

        assertThat(attendance.days().get(0).attendancePercent()).isEqualTo(0.0);
        // Never-showed cannot go negative just because more people turned up
        // than registered - which happens at open events.
        assertThat(attendance.neverShowedCount()).isEqualTo(0);
    }

    // ==================================================================
    // CAMPUS SCOPING
    //
    // These four are regression tests for a cross-tenant read. campusId
    // used to arrive inside EntryLogFilterDto, so any authenticated guard
    // could POST another campus's id and read its whole register; the
    // three path-variable finders had no campus term at all.
    //
    // They assert the campus reaches the REPOSITORY, because that is the
    // only thing that matters. A service that receives a campus and then
    // calls an unscoped finder is exactly the bug being fixed, and would
    // pass any test that only checked the returned list.
    // ==================================================================

    private static final Long OTHER_CAMPUS = 2L;

    @Test
    @DisplayName("byHolder queries within a campus - never by holder id alone")
    void byHolderIsCampusScoped() {
        when(repository.findByCampusIdAndHolderUserIdOrderByScannedAtDesc(
                eq(OTHER_CAMPUS), eq(108L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.byHolder(OTHER_CAMPUS, 108L, org.springframework.data.domain.Pageable.unpaged());

        org.mockito.Mockito.verify(repository)
                .findByCampusIdAndHolderUserIdOrderByScannedAtDesc(eq(OTHER_CAMPUS), eq(108L), any());
    }

    @Test
    @DisplayName("byPass queries within a campus - a pass id from another tenant returns nothing")
    void byPassIsCampusScoped() {
        when(repository.findByCampusIdAndPassIdOrderByScannedAtDesc(CAMPUS_ID, 90231L))
                .thenReturn(List.of());

        assertThat(service.byPass(CAMPUS_ID, 90231L)).isEmpty();

        org.mockito.Mockito.verify(repository)
                .findByCampusIdAndPassIdOrderByScannedAtDesc(CAMPUS_ID, 90231L);
    }

    @Test
    @DisplayName("bySession queries within a campus - a shift id is guessable")
    void bySessionIsCampusScoped() {
        when(repository.findByCampusIdAndSessionIdOrderByScannedAtAsc(CAMPUS_ID, "abc123"))
                .thenReturn(List.of());

        assertThat(service.bySession(CAMPUS_ID, "abc123")).isEmpty();

        org.mockito.Mockito.verify(repository)
                .findByCampusIdAndSessionIdOrderByScannedAtAsc(CAMPUS_ID, "abc123");
    }

    @Test
    @DisplayName("attendance is campus-scoped - FACULTY reach this path and an event id is a URL number")
    void attendanceIsCampusScoped() {
        LocalDate day = LocalDate.now();
        when(repository.findAttendeeIdsForEventDay(eq(CAMPUS_ID), eq(17L), any()))
                .thenReturn(List.of(holder(108L)));

        service.attendance(CAMPUS_ID, 17L, "Summit", day, day, 10);

        // The campus must be in the query itself. Counting another campus's
        // event would be a silent wrong number, not an error.
        org.mockito.Mockito.verify(repository)
                .findAttendeeIdsForEventDay(eq(CAMPUS_ID), eq(17L), any());
    }

    // ------------------------------------------------------------------

    private void countReturns(ScanResult result, long count) {
        when(repository.countByCampusIdAndScanResultAndScannedAtBetween(
                eq(CAMPUS_ID), eq(result), any(), any())).thenReturn(count);
    }

    private EntryLogFilterDto filter() {
        EntryLogFilterDto dto = new EntryLogFilterDto();
        dto.setFrom(FROM);
        dto.setTo(TO);
        return dto;
    }

    private EntryLog holder(Long holderUserId) {
        return EntryLog.builder().holderUserId(holderUserId).build();
    }
}
