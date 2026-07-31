package com.perimity.gatepass.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Repository tests against a REAL PostgreSQL, not H2.
 *
 * ==========================================================================
 *  WHY replace = NONE, AND WHY IT MATTERS MORE THAN IT LOOKS
 * ==========================================================================
 *
 * &#64;DataJpaTest swaps the configured datasource for an in-memory one by
 * default. That default is turned off here for the same reason ci.yml runs
 * real postgres:16 service containers rather than H2: a test that passes
 * against a database we do not deploy is not evidence about the database we do.
 *
 * It matters concretely for THIS class. Two of the methods under test are
 * derived queries whose names are parsed at context startup, and one is
 * hand-written JPQL. A typo in any of them fails the whole application on boot
 * - which is exactly what this test is here to catch early, and it can only
 * catch it against the real dialect.
 *
 * REQUIRES `docker compose up -d`. Without it these fail with "Connection
 * refused", and that is the first thing to check rather than a bug in the test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // The main application.properties reads JWT_SECRET and INTERNAL_API_KEY
        // from the repo-root .env with no fallback, deliberately. Neither is
        // needed by a JPA slice, but the placeholders still have to resolve.
        "perimity.jwt.secret=test-secret-not-used-by-a-jpa-slice",
        "perimity.internal.api-key=test-key-not-used-by-a-jpa-slice"
})
@DisplayName("GatePassRepository - the queries the bulk engine and the sweep depend on")
class GatePassRepositoryTest {

    private static final Long CAMPUS = 1L;
    private static final Long EVENT = 77L;
    private static final Long BATCH = 88L;

    @Autowired
    private GatePassRepository repository;

    /**
     * Needed because findActiveEventIdForHolder JOINS the events table. That is
     * easy to miss: the method reads like a pass lookup, but the pass only
     * supplies the eventId - whether the event is RUNNING is decided by the
     * event row's own window and its cancelled flag, not by the pass dates.
     *
     * A pass valid all month for an event that finished last week correctly
     * returns empty. That is Behavior 2 being right, and it only shows up in a
     * test that persists both sides.
     */
    @Autowired
    private EventRepository events;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        events.deleteAll();
    }

    // -------------------------------- findHolderUserIdsWithLivePassForEvent

    @Test
    @DisplayName("returns holders with a live pass for the event, de-duplicated")
    void findsLiveHoldersForEvent() {
        save(101L, EVENT, PassStatus.ACTIVE, null);
        save(102L, EVENT, PassStatus.PENDING, null);
        save(103L, EVENT, PassStatus.PAUSED, null);
        // Same person twice - DISTINCT must collapse them.
        save(101L, EVENT, PassStatus.ACTIVE, null);

        assertThat(repository.findHolderUserIdsWithLivePassForEvent(EVENT))
                .containsExactlyInAnyOrder(101L, 102L, 103L);
    }

    @Test
    @DisplayName("EXCLUDES a revoked pass - a revoked holder must be issuable again")
    void excludesRevoked() {
        save(201L, EVENT, PassStatus.REVOKED, null);

        assertThat(repository.findHolderUserIdsWithLivePassForEvent(EVENT)).isEmpty();
    }

    @Test
    @DisplayName("does not leak holders from a different event")
    void scopedToOneEvent() {
        save(301L, EVENT, PassStatus.ACTIVE, null);
        save(302L, 999L, PassStatus.ACTIVE, null);

        assertThat(repository.findHolderUserIdsWithLivePassForEvent(EVENT))
                .containsExactly(301L);
    }

    // ------------------------------------------------------- batch queries

    @Test
    @DisplayName("countByBatchIdAndStatus drives the progress bar")
    void countsBatchByStatus() {
        save(401L, EVENT, PassStatus.ACTIVE, BATCH);
        save(402L, EVENT, PassStatus.ACTIVE, BATCH);
        save(403L, EVENT, PassStatus.PENDING, BATCH);
        // A pass from a single approval - no batch. Must not be counted.
        save(404L, EVENT, PassStatus.ACTIVE, null);

        assertThat(repository.countByBatchIdAndStatus(BATCH, PassStatus.ACTIVE)).isEqualTo(2);
        assertThat(repository.countByBatchIdAndStatus(BATCH, PassStatus.PENDING)).isEqualTo(1);
    }

    @Test
    @DisplayName("findByBatchIdAndStatus(PENDING) finds exactly the stuck rows to retry")
    void findsStuckRows() {
        save(501L, EVENT, PassStatus.ACTIVE, BATCH);
        save(502L, EVENT, PassStatus.PENDING, BATCH);
        save(503L, EVENT, PassStatus.PENDING, BATCH);

        assertThat(repository.findByBatchIdAndStatus(BATCH, PassStatus.PENDING))
                .hasSize(2)
                .extracting(GatePass::getHolderUserId)
                .containsExactlyInAnyOrder(502L, 503L);
    }

    // --------------------------------------------------------- expiry sweep

    @Test
    @DisplayName("findExpiredPasses ignores a DAILY pass with a null validTo")
    void standingPassNeverExpires() {
        // A student's standing pass: no end date. The sweep must never touch it.
        repository.save(GatePass.builder()
                .holderUserId(601L).holderName("Standing Student").campusId(CAMPUS)
                .passType(PassType.DAILY).validFrom(LocalDate.now().minusYears(1))
                .validTo(null).status(PassStatus.ACTIVE).build());

        // An event pass that ended yesterday: the sweep must catch this one.
        repository.save(GatePass.builder()
                .holderUserId(602L).holderName("Stale Visitor").campusId(CAMPUS)
                .passType(PassType.EVENT).eventId(EVENT)
                .validFrom(LocalDate.now().minusDays(5))
                .validTo(LocalDate.now().minusDays(1))
                .status(PassStatus.ACTIVE).build());

        List<GatePass> due = repository.findExpiredPasses(LocalDate.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getHolderUserId()).isEqualTo(602L);
    }

    // --------------------------------------------------- Behavior 2 support

    @Test
    @DisplayName("findActiveEventIdForHolder returns the event running today")
    void findsRunningEventForHolder() {
        Long liveEvent = anEvent(LocalDate.now().minusDays(1),
                                 LocalDate.now().plusDays(1), false);
        givenEventPass(701L, liveEvent);

        assertThat(repository.findActiveEventIdForHolder(701L, LocalDate.now()))
                .contains(liveEvent);
    }

    @Test
    @DisplayName("empty once the EVENT window has passed, even if the pass is still valid")
    void noRunningEventAfterTheWindow() {
        Long finishedEvent = anEvent(LocalDate.now().minusDays(10),
                                     LocalDate.now().minusDays(5), false);

        // The pass itself is still in date. The EVENT is not. Behavior 2 must
        // follow the event, otherwise a stale pass keeps crediting attendance
        // to a programme that ended last week.
        repository.save(GatePass.builder()
                .holderUserId(801L).holderName("Late Arrival").campusId(CAMPUS)
                .passType(PassType.EVENT).eventId(finishedEvent)
                .validFrom(LocalDate.now().minusDays(10))
                .validTo(LocalDate.now().plusDays(30))
                .status(PassStatus.ACTIVE).build());

        assertThat(repository.findActiveEventIdForHolder(801L, LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("empty when the event is running but CANCELLED")
    void cancelledEventDoesNotAttribute() {
        Long cancelled = anEvent(LocalDate.now().minusDays(1),
                                 LocalDate.now().plusDays(1), true);
        givenEventPass(901L, cancelled);

        assertThat(repository.findActiveEventIdForHolder(901L, LocalDate.now())).isEmpty();
    }

    private Long anEvent(LocalDate from, LocalDate to, boolean cancelled) {
        return events.save(Event.builder()
                .campusId(CAMPUS)
                .name("Programme " + from.toString())
                .validFrom(from)
                .validTo(to)
                .createdBy(1L)
                .cancelled(cancelled)
                .build()).getId();
    }

    private void givenEventPass(Long holder, Long eventId) {
        repository.save(GatePass.builder()
                .holderUserId(holder).holderName(nameFor(holder)).campusId(CAMPUS)
                .passType(PassType.EVENT).eventId(eventId)
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(1))
                .status(PassStatus.ACTIVE).build());
    }

    // ------------------------------------------------------------- helper

    /**
     * Letters only, no digits.
     *
     * The first version of this helper used "Holder " + id and every insert
     * failed with a ConstraintViolationException on holderName. That was not a
     * flaw in the test - it is PERSON_NAME doing its job. The pattern allows
     * letters, spaces, apostrophes, hyphens and full stops, and nothing else.
     * Worth leaving this note: the obvious test-data helper does not work here,
     * and the reason is a feature.
     */
    private String nameFor(Long holder) {
        return "Holder " + Long.toString(holder).chars()
                .mapToObj(c -> String.valueOf((char) ('A' + (c - '0'))))
                .reduce("", String::concat);
    }

    private void save(Long holder, Long eventId, PassStatus status, Long batchId) {
        repository.save(GatePass.builder()
                .holderUserId(holder)
                .holderName(nameFor(holder))
                .campusId(CAMPUS)
                .passType(PassType.EVENT)
                .eventId(eventId)
                .batchId(batchId)
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusDays(3))
                .status(status)
                .build());
    }
}
