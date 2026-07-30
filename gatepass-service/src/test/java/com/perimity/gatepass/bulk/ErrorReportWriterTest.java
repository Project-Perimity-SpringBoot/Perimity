package com.perimity.gatepass.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.gatepass.dto.response.RowErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The error report is the one artefact in this system that takes
 * attacker-controlled text and hands it to an administrator, in a format their
 * spreadsheet will happily execute. These tests are about that.
 */
@DisplayName("ErrorReportWriter - CSV safety")
class ErrorReportWriterTest {

    private final ErrorReportWriter writer = new ErrorReportWriter();

    private String csv(List<RowErrorResponse> errors) {
        return new String(writer.write(errors), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("starts with a UTF-8 BOM so Excel does not mangle non-ASCII names")
    void hasBom() {
        // Without it, Excel on Windows opens a UTF-8 CSV in the system codepage
        // and every accented name is mojibake - in a report whose entire job is
        // to help someone FIX names.
        String out = csv(List.of(RowErrorResponse.of(2, "a@example.org", "Nope")));

        assertThat(out).startsWith("\uFEFF");
    }

    @Test
    @DisplayName("neutralises a leading = so the spreadsheet treats it as text")
    void neutralisesEquals() {
        String out = csv(List.of(RowErrorResponse.of(
                51, "=HYPERLINK(\"http://evil/\",\"click\")", "Bad name")));

        assertThat(out).contains("\"'=HYPERLINK");
    }

    @Test
    @DisplayName("neutralises + - and @ as well, not just =")
    void neutralisesOtherFormulaStarters() {
        String out = csv(List.of(
                RowErrorResponse.of(1, "+41791234567", "plus"),
                RowErrorResponse.of(2, "-1+1", "minus"),
                RowErrorResponse.of(3, "@handle", "at")));

        assertThat(out).contains("\"'+41791234567\"");
        assertThat(out).contains("\"'-1+1\"");
        assertThat(out).contains("\"'@handle\"");
    }

    @Test
    @DisplayName("doubles an embedded quote, per RFC 4180")
    void escapesQuotes() {
        String out = csv(List.of(RowErrorResponse.of(2, "quote\"inside", "Bad")));

        assertThat(out).contains("\"quote\"\"inside\"");
    }

    @Test
    @DisplayName("flattens a newline so one bad row stays one line")
    void flattensNewlines() {
        String out = csv(List.of(RowErrorResponse.of(2, "line\nbreak", "Bad")));

        assertThat(out).contains("\"line break\"");
        // Header + one data row + trailing terminator.
        assertThat(out.split("\r\n")).hasSize(2);
    }

    @Test
    @DisplayName("a comma in a value does not shift the columns")
    void handlesCommas() {
        String out = csv(List.of(RowErrorResponse.of(2, "a@example.org", "Comma, inside")));

        assertThat(out).contains("\"Comma, inside\"");
    }

    @Test
    @DisplayName("a null value becomes an empty field rather than the text 'null'")
    void nullBecomesEmpty() {
        String out = csv(List.of(RowErrorResponse.of(2, null, "Email is required")));

        assertThat(out).contains("2,,\"Email is required\"");
    }

    @Test
    @DisplayName("an empty error list still produces a valid file with a header")
    void emptyListStillHasHeader() {
        String out = csv(List.of());

        assertThat(out).isEqualTo("\uFEFFRow,Email,Problem\r\n");
    }
}
