package com.perimity.gatepass.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.response.RowErrorResponse;
import com.perimity.gatepass.repository.GatePassRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Row validation, including the mixed-attendee rules.
 *
 * This is the logic the Event &amp; Bulk design document is most specific about,
 * and the part a viva question is most likely to land on: 600 attendees, 102 of
 * whom are already students, and the faculty member uploading the sheet should
 * not have to know which.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BulkValidationService - per-row rules")
class BulkValidationServiceTest {

    private static final Long CAMPUS = 1L;
    private static final Long EVENT = 7L;

    @Mock private GatePassRepository passRepository;
    @Mock private InternalServiceClient internal;

    @InjectMocks private BulkValidationService service;

    @BeforeEach
    void noBlocklistByDefault() {
        // lenient: not every test reaches the blocklist check, because the
        // shape check short-circuits first. Strict stubbing would fail those.
        lenient().when(internal.isBlocklisted(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(false));
        lenient().when(internal.findUserIdByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(passRepository.findHolderUserIdsWithLivePassForEvent(anyLong()))
                .thenReturn(List.of());
    }

    // ------------------------------------------------------------- happy path

    @Test
    @DisplayName("a well-formed sheet produces no errors")
    void allValid() {
        var outcome = service.validate(List.of(
                row(2, "Asha Menon", "asha@example.org"),
                row(3, "Ravi Iyer", "ravi@example.org")), CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(2);
        assertThat(outcome.invalidCount()).isZero();
    }

    @Test
    @DisplayName("optional columns may be blank without failing the row")
    void optionalColumnsMayBeBlank() {
        var outcome = service.validate(List.of(
                new ParsedRow(2, "Asha Menon", "asha@example.org", null, null)),
                CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------ shape rules

    @Test
    @DisplayName("a malformed email is rejected with a readable reason")
    void malformedEmail() {
        var outcome = service.validate(List.of(
                row(2, "Broken Row", "not-an-email")), CAMPUS, EVENT);

        assertThat(outcome.invalidCount()).isEqualTo(1);
        assertThat(outcome.errors().get(0).reason()).isEqualTo("Not a valid email address");
    }

    @Test
    @DisplayName("a missing name is rejected")
    void missingName() {
        var outcome = service.validate(List.of(
                new ParsedRow(2, null, "asha@example.org", null, null)), CAMPUS, EVENT);

        assertThat(outcome.errors().get(0).reason()).isEqualTo("Name is required");
    }

    @Test
    @DisplayName("a name containing a NEWLINE is rejected - the \\s regex fix")
    void newlineInNameRejected() {
        // Before PERSON_NAME was corrected, Java's \\s matched \\n and this row
        // was accepted. A name with a newline ends up in the CSV error report
        // that a Campus Admin opens.
        var outcome = service.validate(List.of(
                row(2, "Asha\nMenon", "asha@example.org")), CAMPUS, EVENT);

        assertThat(outcome.invalidCount()).isEqualTo(1);
        assertThat(outcome.errors().get(0).reason())
                .isEqualTo("Name contains characters that are not allowed");
    }

    @Test
    @DisplayName("accented and Devanagari names are accepted")
    void unicodeNamesAccepted() {
        var outcome = service.validate(List.of(
                row(2, "Jos\u00e9 O'Brien", "jose@example.org"),
                row(3, "\u0905\u0936\u093e \u0935\u0930\u094d\u092e\u093e", "asha.v@example.org")),
                CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(2);
    }

    // -------------------------------------------------------------- duplicates

    @Test
    @DisplayName("the same email twice in one sheet keeps the first and rejects the second")
    void duplicateInSheet() {
        var outcome = service.validate(List.of(
                row(2, "Asha Menon", "asha@example.org"),
                row(3, "Asha Menon", "asha@example.org")), CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(1);
        assertThat(outcome.errors())
                .extracting(RowErrorResponse::rowNumber)
                .containsExactly(3);
        assertThat(outcome.errors().get(0).reason())
                .isEqualTo("This email appears more than once in the sheet");
    }

    @Test
    @DisplayName("duplicate detection is case-insensitive")
    void duplicateIgnoresCase() {
        var outcome = service.validate(List.of(
                row(2, "Asha Menon", "Asha@Example.org"),
                row(3, "Asha Menon", "asha@example.org")), CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(1);
        assertThat(outcome.invalidCount()).isEqualTo(1);
    }

    // -------------------------------------------------- mixed attendee rules

    @Test
    @DisplayName("someone who already holds a pass for THIS event is rejected")
    void alreadyHasPassForEvent() {
        when(internal.findUserIdByEmail("existing@example.org")).thenReturn(Optional.of(500L));
        when(passRepository.findHolderUserIdsWithLivePassForEvent(EVENT))
                .thenReturn(List.of(500L));

        var outcome = service.validate(List.of(
                row(2, "Existing Attendee", "existing@example.org")), CAMPUS, EVENT);

        assertThat(outcome.errors().get(0).reason())
                .isEqualTo("This person already holds a pass for this event");
    }

    @Test
    @DisplayName("an EXISTING student with no pass for this event is accepted, not duplicated")
    void existingStudentAccepted() {
        // The 102-of-600 case. They have an identity; they do not have a pass
        // for this programme; they get one and no second account.
        when(internal.findUserIdByEmail("student@example.org")).thenReturn(Optional.of(600L));
        when(passRepository.findHolderUserIdsWithLivePassForEvent(EVENT)).thenReturn(List.of());

        var outcome = service.validate(List.of(
                row(2, "Existing Student", "student@example.org")), CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a DAILY batch skips the already-has-a-pass check entirely")
    void dailyBatchSkipsEventCheck() {
        service.validate(List.of(row(2, "New Student", "new@example.org")), CAMPUS, null);

        // No event means the question is meaningless - and asking it would be
        // a wasted query per row.
        verify(passRepository, times(0)).findHolderUserIdsWithLivePassForEvent(any());
    }

    // -------------------------------------------------------------- blocklist

    @Test
    @DisplayName("a blocklisted email is rejected")
    void blocklisted() {
        when(internal.isBlocklisted(CAMPUS, "barred@example.org", null))
                .thenReturn(Optional.of(true));

        var outcome = service.validate(List.of(
                row(2, "Barred Person", "barred@example.org")), CAMPUS, EVENT);

        assertThat(outcome.errors().get(0).reason())
                .isEqualTo("This email is on this campus's blocklist");
    }

    @Test
    @DisplayName("FAILS OPEN when auth-service cannot answer")
    void blocklistUnavailableAllowsRow() {
        // Deliberate. If auth-service is restarting, rejecting all 600 rows of
        // a legitimate upload as "blocklisted" is both wrong and alarming. A
        // barred visitor who slips through is still stopped at the gate,
        // because the guard scan checks pass status live.
        when(internal.isBlocklisted(anyLong(), anyString(), any())).thenReturn(Optional.empty());

        var outcome = service.validate(List.of(
                row(2, "Unknown Status", "unknown@example.org")), CAMPUS, EVENT);

        assertThat(outcome.validCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the blocklist answer is memoised - one call per distinct email")
    void blocklistMemoised() {
        // A sheet listing the same address twenty times must make one call,
        // not twenty.
        service.validate(List.of(
                row(2, "Asha Menon", "same@example.org"),
                row(3, "Asha Menon", "same@example.org"),
                row(4, "Asha Menon", "same@example.org")), CAMPUS, EVENT);

        verify(internal, times(1)).isBlocklisted(anyLong(), anyString(), any());
    }

    // ------------------------------------------------------------- reporting

    @Test
    @DisplayName("inline errors are capped, the full list is not")
    void inlineErrorsCapped() {
        List<ParsedRow> many = java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> row(i + 1, "Broken Row", "bad-email-" + i))
                .toList();

        var outcome = service.validate(many, CAMPUS, EVENT);

        assertThat(outcome.invalidCount()).isEqualTo(60);
        assertThat(outcome.inlineErrors())
                .hasSize(BulkValidationService.MAX_ERRORS_INLINE);
    }

    @Test
    @DisplayName("row numbers in errors are the ones the user sees in Excel")
    void rowNumbersArePreserved() {
        var outcome = service.validate(List.of(
                row(2, "Fine Person", "fine@example.org"),
                row(34, "Broken Row", "nope")), CAMPUS, EVENT);

        assertThat(outcome.errors().get(0).rowNumber()).isEqualTo(34);
    }

    private ParsedRow row(int n, String name, String email) {
        return new ParsedRow(n, name, email, null, "Attending");
    }
}
