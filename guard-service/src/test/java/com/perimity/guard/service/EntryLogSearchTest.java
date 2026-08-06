package com.perimity.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.perimity.guard.document.EntryLog;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.dto.request.EntryLogFilterDto;
import com.perimity.guard.repository.EntryLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * search(), which had no test at all while it was quietly wrong.
 *
 * It chose between two derived repository methods, and only one of them
 * honoured from/to - so narrowing to DENIED widened the search to all of
 * history. Every test passed, because none of them called search. These assert
 * the QUERY rather than the plumbing, so the range cannot be dropped again
 * without something going red.
 */
@ExtendWith(MockitoExtension.class)
class EntryLogSearchTest {

    private static final Long CAMPUS = 7L;
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 8, 23, 59);

    @Mock private EntryLogRepository repository;
    @Mock private MongoTemplate mongo;

    private EntryLogService service;

    @BeforeEach
    void setUp() {
        service = new EntryLogService(repository, mongo);
        given(mongo.find(any(Query.class), eq(EntryLog.class))).willReturn(List.of());
        given(mongo.count(any(Query.class), eq(EntryLog.class))).willReturn(0L);
    }

    private EntryLogFilterDto filter() {
        EntryLogFilterDto f = new EntryLogFilterDto();
        f.setFrom(FROM);
        f.setTo(TO);
        return f;
    }

    /** Captures the query actually handed to Mongo. */
    private String queryOf(EntryLogFilterDto f) {
        service.search(CAMPUS, f, PageRequest.of(0, 10));
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(EntryLog.class));
        // toString, not toJson - toJson needs a codec for LocalDateTime and this
        // only ever inspects the shape of the criteria.
        return q.getValue().getQueryObject().toString();
    }

    /** THE REGRESSION: the range must survive a result filter. */
    @Test
    void keepsTheDateRangeWhenFilteringToDenials() {
        EntryLogFilterDto f = filter();
        f.setScanResult(ScanResult.DENIED);

        String json = queryOf(f);

        assertThat(json).contains("scannedAt");
        assertThat(json).contains("scanResult");
    }

    @Test
    void alwaysScopesToTheCallersCampus() {
        assertThat(queryOf(filter())).contains("campusId");
    }

    @Test
    void searchesTheWholeRangeNotJustTheLoadedPage() {
        EntryLogFilterDto f = filter();
        f.setQuery("Anita");

        String json = queryOf(f);

        assertThat(json).contains("holderName");
        assertThat(json).contains("gateName");
        assertThat(json).contains("scannedAt");
    }

    /**
     * A term is data, not a pattern. "Gate 2." must look for that text, not for
     * "Gate 2" followed by any character.
     */
    @Test
    void treatsTheSearchTermAsLiteralText() {
        EntryLogFilterDto f = filter();
        f.setQuery("Gate 2.");

        assertThat(queryOf(f)).contains("\\Q");
    }

    @Test
    void ignoresABlankSearchTerm() {
        EntryLogFilterDto f = filter();
        f.setQuery("   ");

        assertThat(queryOf(f)).doesNotContain("holderName");
    }

    /** The count must not inherit the page's skip/limit, or totals go wrong. */
    @Test
    void countsTheWholeResultSetNotThePage() {
        service.search(CAMPUS, filter(), PageRequest.of(3, 10));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(q.capture(), eq(EntryLog.class));

        // -1 is how Spring Data clears a limit/skip; either way it must not be
        // carrying page 3's window of 10.
        assertThat(q.getValue().getLimit()).isLessThanOrEqualTo(0);
        assertThat(q.getValue().getSkip()).isLessThanOrEqualTo(0);
    }
}
