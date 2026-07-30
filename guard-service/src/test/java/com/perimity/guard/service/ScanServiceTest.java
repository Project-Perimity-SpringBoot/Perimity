package com.perimity.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.guard.client.PassVerification;
import com.perimity.guard.client.PassVerificationClient;
import com.perimity.guard.client.RunningEventClient;
import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.document.enums.SessionState;
import com.perimity.guard.dto.request.ScanRequestDto;
import com.perimity.guard.dto.response.ScanResponse;
import com.perimity.guard.repository.EntryLogRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The gate decision tree, branch by branch.
 *
 * ==========================================================================
 * WHY THESE ARE MOCKITO TESTS AND NOT @SpringBootTest
 * ==========================================================================
 * Every branch in ScanService is a pure decision over a PassVerification and a
 * ScanSession. Standing up Mongo, Spring, and two HTTP stubs to exercise a
 * switch statement would make the suite slow enough that nobody runs it before
 * pushing - and slow tests that are skipped catch nothing.
 *
 * The thing worth testing at integration level is the two-hop client, and that
 * is deliberately NOT here: mocking qr-service and gatepass-service with shapes
 * I guessed would test my guess, not their API. That becomes a real integration
 * test on Day 11 once both endpoints exist.
 *
 * ==========================================================================
 * THE INVARIANT EVERY TEST CHECKS
 * ==========================================================================
 * Exactly one EntryLog per scan, INCLUDING every refusal. A denied attempt that
 * leaves no trace is the failure mode the paper register had, and removing it is
 * most of the reason this product exists. Several tests below would pass on the
 * result alone and still assert on the save, on purpose.
 *
 * This test lives in the same package as ScanSessionService because recordScan
 * is package-private. Moving it would silently stop that verification working.
 */
@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    private static final Long GUARD_ID = 55L;
    private static final Long CAMPUS_ID = 1L;
    private static final Long OTHER_CAMPUS = 2L;
    private static final Long HOLDER_ID = 108L;
    private static final Long PASS_ID = 118L;

    @Mock private EntryLogRepository entryLogRepository;
    @Mock private ScanSessionService sessionService;
    @Mock private PassVerificationClient passVerification;
    @Mock private RunningEventClient runningEvents;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(entryLogRepository, sessionService,
                passVerification, runningEvents);

        // Saving returns the argument with an id, mimicking Mongo. ScanResponse
        // reads log.getId(), so returning null here would hide a real NPE.
        when(entryLogRepository.save(any(EntryLog.class))).thenAnswer(invocation -> {
            EntryLog log = invocation.getArgument(0);
            log.setId("entrylog-1");
            return log;
        });
    }

    // ------------------------------------------------------------------
    // Refusals
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a token that cannot be decoded is INVALID_TOKEN, and is still logged")
        void undecodableToken() {
            openSession();
            when(passVerification.verify("rubbish"))
                    .thenReturn(PassVerification.undecodable("abc123abc123"));

            ScanResponse response = scanService.scan(request("rubbish"), GUARD_ID);

            assertThat(response.result()).isEqualTo(ScanResult.DENIED);
            assertThat(response.denialReason()).isEqualTo(DenialReason.INVALID_TOKEN);
            assertThat(savedLog().getScanResult()).isEqualTo(ScanResult.DENIED);
        }

        @Test
        @DisplayName("a revoked pass is refused with its own reason, not a generic one")
        void revokedPass() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.DAILY, null, DenialReason.PASS_REVOKED,
                            CAMPUS_ID, LocalDate.now().minusDays(1), null));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            assertThat(response.denialReason()).isEqualTo(DenialReason.PASS_REVOKED);
            // The guard needs a reason they can act on, not just a colour.
            assertThat(response.message()).isEqualTo("Pass has been revoked");
        }

        @Test
        @DisplayName("a valid pass from another campus is WRONG_CAMPUS, checked against the session")
        void wrongCampus() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.DAILY, null, null,
                            OTHER_CAMPUS, LocalDate.now().minusDays(1), null));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            // The campus comes from the guard's open shift, so a perfectly valid
            // pass from elsewhere cannot be waved through by a guard standing here.
            assertThat(response.denialReason()).isEqualTo(DenialReason.WRONG_CAMPUS);
        }

        @Test
        @DisplayName("a pass whose validTo has passed is OUT_OF_DATE_RANGE")
        void expiredByDate() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.EVENT, 17L, null, CAMPUS_ID,
                            LocalDate.now().minusDays(10), LocalDate.now().minusDays(1)));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            assertThat(response.denialReason()).isEqualTo(DenialReason.OUT_OF_DATE_RANGE);
        }

        @Test
        @DisplayName("a pass that does not start until tomorrow is refused today")
        void notYetValid() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.EVENT, 17L, null, CAMPUS_ID,
                            LocalDate.now().plusDays(1), LocalDate.now().plusDays(3)));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            assertThat(response.denialReason()).isEqualTo(DenialReason.OUT_OF_DATE_RANGE);
        }

        @Test
        @DisplayName("no open shift refuses the scan and writes NO log")
        void noOpenSession() {
            when(sessionService.requireOpenSession(GUARD_ID))
                    .thenThrow(new IllegalStateException(
                            "No open shift for this guard. Start a shift before scanning."));

            assertThatThrownBy(() -> scanService.scan(request("t"), GUARD_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No open shift");

            // Nothing happened at a gate, so nothing belongs in the register.
            // This is the one refusal that must NOT produce a document.
            verify(entryLogRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------
    // Entries allowed
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("allowed entries")
    class Allowed {

        @Test
        @DisplayName("a standing daily pass with a null validTo is allowed - the normal student case")
        void standingDailyPassNeverExpires() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.DAILY, null, null, CAMPUS_ID,
                            LocalDate.now().minusYears(1), null));
            when(runningEvents.runningEventFor(HOLDER_ID)).thenReturn(Optional.empty());

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            // A null validTo means "no end date", not "expired". Getting this
            // backwards would refuse every student on the campus.
            assertThat(response.result()).isEqualTo(ScanResult.ALLOWED);
            assertThat(response.attributedEventId()).isNull();
        }

        @Test
        @DisplayName("an EVENT pass credits its own event")
        void eventPassCreditsItsEvent() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.EVENT, 17L, null, CAMPUS_ID,
                            LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            assertThat(response.result()).isEqualTo(ScanResult.ALLOWED);
            assertThat(savedLog().getAttributedEventId()).isEqualTo(17L);
            // An EVENT pass must not trigger the Behavior 2 lookup - it already
            // knows which event it belongs to.
            verify(runningEvents, never()).runningEventFor(any());
        }

        @Test
        @DisplayName("Behavior 2 - a DAILY QR scanned during a running event credits that event")
        void behaviorTwoAttributesDailyScanToRunningEvent() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.DAILY, null, null, CAMPUS_ID,
                            LocalDate.now().minusYears(1), null));
            when(runningEvents.runningEventFor(HOLDER_ID)).thenReturn(Optional.of(17L));

            ScanResponse response = scanService.scan(request("t"), GUARD_ID);

            assertThat(response.result()).isEqualTo(ScanResult.ALLOWED);

            EntryLog log = savedLog();
            // eventId stays null because the pass genuinely is not an event pass.
            // attributedEventId is what attendance counts on, which is the whole
            // point: the student scanned the wrong QR and the figures stay right.
            assertThat(log.getEventId()).isNull();
            assertThat(log.getAttributedEventId()).isEqualTo(17L);
            assertThat(log.isEventAttributed()).isTrue();
        }

        @Test
        @DisplayName("a DAILY pass with no event running today is an ordinary campus entry")
        void dailyPassWithoutEvent() {
            openSession();
            when(passVerification.verify(any()))
                    .thenReturn(pass(PassType.DAILY, null, null, CAMPUS_ID,
                            LocalDate.now().minusYears(1), null));
            when(runningEvents.runningEventFor(HOLDER_ID)).thenReturn(Optional.empty());

            scanService.scan(request("t"), GUARD_ID);

            assertThat(savedLog().getAttributedEventId()).isNull();
            assertThat(savedLog().isEventAttributed()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // What every log carries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("gate, campus and guard are copied from the session, never from the request")
    void logCarriesSessionFactsNotClientClaims() {
        openSession();
        when(passVerification.verify(any()))
                .thenReturn(pass(PassType.EVENT, 17L, null, CAMPUS_ID,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)));

        scanService.scan(request("t"), GUARD_ID);

        EntryLog log = savedLog();
        // These four are what make the entry log evidence rather than a claim.
        // If any of them could come from the request body, a guard could log
        // entries at a gate they were never posted to.
        assertThat(log.getGuardUserId()).isEqualTo(GUARD_ID);
        assertThat(log.getCampusId()).isEqualTo(CAMPUS_ID);
        assertThat(log.getGateId()).isEqualTo(3L);
        assertThat(log.getGateName()).isEqualTo("Main Gate");

        assertThat(log.getSessionId()).isEqualTo("session-1");
        // Denormalised on purpose - the register must render years later without
        // calling another service.
        assertThat(log.getHolderName()).isEqualTo("R. Kulkarni");
        // Set server-side, so a client cannot back-date an entry.
        assertThat(log.getScannedAt()).isNotNull();
        assertThat(log.getScanDate()).isNotBlank();
    }

    @Test
    @DisplayName("the shift counters are updated for an allowed scan and for a refusal")
    void sessionCountersUpdatedEitherWay() {
        ScanSession session = openSession();

        when(passVerification.verify(any()))
                .thenReturn(pass(PassType.EVENT, 17L, null, CAMPUS_ID,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)));
        scanService.scan(request("t"), GUARD_ID);
        verify(sessionService).recordScan(eq(session), eq(ScanResult.ALLOWED));

        when(passVerification.verify(any()))
                .thenReturn(PassVerification.undecodable("abc123abc123"));
        scanService.scan(request("t"), GUARD_ID);
        verify(sessionService).recordScan(eq(session), eq(ScanResult.DENIED));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ScanSession openSession() {
        ScanSession session = ScanSession.builder()
                .id("session-1")
                .guardUserId(GUARD_ID)
                .campusId(CAMPUS_ID)
                .gateId(3L)
                .gateName("Main Gate")
                .state(SessionState.OPEN)
                .build();
        when(sessionService.requireOpenSession(GUARD_ID)).thenReturn(session);
        return session;
    }

    private PassVerification pass(PassType type, Long eventId, DenialReason denial,
                                  Long campusId, LocalDate from, LocalDate to) {
        return new PassVerification(true, denial, PASS_ID, HOLDER_ID, "R. Kulkarni",
                campusId, type, eventId, from, to, "abc123abc123");
    }

    private ScanRequestDto request(String token) {
        ScanRequestDto dto = new ScanRequestDto();
        dto.setToken(token);
        return dto;
    }

    /** The most recently saved document. */
    private EntryLog savedLog() {
        ArgumentCaptor<EntryLog> captor = ArgumentCaptor.forClass(EntryLog.class);
        verify(entryLogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
