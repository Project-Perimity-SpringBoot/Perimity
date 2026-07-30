package com.perimity.gatepass.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spreadsheet parsing, against workbooks built in the test itself.
 *
 * Built rather than checked in as fixture files, on purpose: a binary .xlsx in
 * the repo cannot be reviewed in a pull request. Anyone reading this class can
 * see exactly what the sheet contains, which is the whole point when the
 * behaviour under test is "what does this file actually say".
 */
@DisplayName("SheetParser - reading an uploaded .xlsx")
class SheetParserTest {

    private final SheetParser parser = new SheetParser();

    @Test
    @DisplayName("reads the four standard columns")
    void readsStandardColumns() {
        byte[] xlsx = sheet(
                new String[]{"name", "email", "phone", "purpose"},
                new Object[]{"Asha Menon", "asha@example.org", "9876543210", "Attending"});

        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(xlsx), 1000);

        assertThat(rows).hasSize(1);
        ParsedRow r = rows.get(0);
        assertThat(r.name()).isEqualTo("Asha Menon");
        assertThat(r.email()).isEqualTo("asha@example.org");
        assertThat(r.phone()).isEqualTo("9876543210");
        assertThat(r.purpose()).isEqualTo("Attending");
    }

    @Test
    @DisplayName("row numbers are 1-based and include the header")
    void rowNumbersMatchExcel() {
        // "row 34" in an error report has to mean row 34 in the file the user
        // is looking at. Reporting a zero-based index makes the report useless.
        byte[] xlsx = sheet(
                new String[]{"name", "email"},
                new Object[]{"First Person", "a@example.org"},
                new Object[]{"Second Person", "b@example.org"});

        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(xlsx), 1000);

        assertThat(rows).extracting(ParsedRow::rowNumber).containsExactly(2, 3);
    }

    @Test
    @DisplayName("HEADERS ARE MATCHED BY NAME, so a reordered sheet still works")
    void headersMatchedByName() {
        // Position-based reading fails SILENTLY here - every row would be
        // "valid" with the phone number in the name field.
        byte[] xlsx = sheet(
                new String[]{"purpose", "phone", "email", "name"},
                new Object[]{"Attending", "9876543210", "asha@example.org", "Asha Menon"});

        ParsedRow r = parser.parse(new ByteArrayInputStream(xlsx), 1000).get(0);

        assertThat(r.name()).isEqualTo("Asha Menon");
        assertThat(r.email()).isEqualTo("asha@example.org");
    }

    @Test
    @DisplayName("common header spellings are accepted")
    void headerAliases() {
        byte[] xlsx = sheet(
                new String[]{"Full Name", "E-Mail ID", "Mobile No", "Purpose of Visit"},
                new Object[]{"Asha Menon", "asha@example.org", "9876543210", "Attending"});

        ParsedRow r = parser.parse(new ByteArrayInputStream(xlsx), 1000).get(0);

        assertThat(r.name()).isEqualTo("Asha Menon");
        assertThat(r.email()).isEqualTo("asha@example.org");
        assertThat(r.phone()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("a NUMERIC phone cell does not come back in scientific notation")
    void numericPhoneCell() {
        // Excel stores a phone typed into a General cell as a double. Raw POI
        // gives 9.87654321E9. This is the single most common data corruption in
        // spreadsheet imports.
        byte[] xlsx = sheet(
                new String[]{"name", "email", "phone"},
                new Object[]{"Asha Menon", "asha@example.org", 9876543210d});

        ParsedRow r = parser.parse(new ByteArrayInputStream(xlsx), 1000).get(0);

        assertThat(r.phone()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("blank rows are skipped and not counted")
    void blankRowsSkipped() {
        // Excel routinely reports thousands of trailing rows that only ever
        // held formatting. Counting them reports "5000 rows, 4400 errors" for
        // a 600-row sheet.
        byte[] xlsx = sheet(
                new String[]{"name", "email"},
                new Object[]{"Asha Menon", "asha@example.org"},
                new Object[]{"", ""},
                new Object[]{"Ravi Iyer", "ravi@example.org"});

        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(xlsx), 1000);

        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed")
    void trimsWhitespace() {
        byte[] xlsx = sheet(
                new String[]{"name", "email"},
                new Object[]{"  Asha Menon  ", "  asha@example.org  "});

        ParsedRow r = parser.parse(new ByteArrayInputStream(xlsx), 1000).get(0);

        assertThat(r.name()).isEqualTo("Asha Menon");
        assertThat(r.email()).isEqualTo("asha@example.org");
    }

    @Test
    @DisplayName("emailKey lowercases, so duplicate detection is case-insensitive")
    void emailKeyLowercases() {
        byte[] xlsx = sheet(
                new String[]{"name", "email"},
                new Object[]{"Asha Menon", "Asha@Example.ORG"});

        assertThat(parser.parse(new ByteArrayInputStream(xlsx), 1000).get(0).emailKey())
                .isEqualTo("asha@example.org");
    }

    // ------------------------------------------------------- rejected files

    @Test
    @DisplayName("a sheet missing the email column is rejected with a helpful message")
    void missingRequiredColumn() {
        byte[] xlsx = sheet(
                new String[]{"name", "phone"},
                new Object[]{"Asha Menon", "9876543210"});

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(xlsx), 1000))
                .isInstanceOf(SheetParser.UnreadableSheetException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("template");
    }

    @Test
    @DisplayName("a header-only sheet is rejected")
    void headerOnly() {
        byte[] xlsx = sheet(new String[]{"name", "email"});

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(xlsx), 1000))
                .isInstanceOf(SheetParser.UnreadableSheetException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    @DisplayName("the row limit is enforced DURING the read, not after")
    void rowLimitEnforced() {
        // Checked while reading so a hostile five-million-row file cannot
        // exhaust heap before anyone gets to reject it.
        Object[][] rows = new Object[10][];
        for (int i = 0; i < 10; i++) {
            rows[i] = new Object[]{"Person Number", "p" + i + "@example.org"};
        }
        byte[] xlsx = sheet(new String[]{"name", "email"}, rows);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(xlsx), 5))
                .isInstanceOf(SheetParser.UnreadableSheetException.class)
                .hasMessageContaining("more than 5 rows");
    }

    @Test
    @DisplayName("a file that is not really an xlsx is rejected with plain-English advice")
    void notAnXlsx() {
        InputStream notASpreadsheet =
                new ByteArrayInputStream("name,email\nAsha,a@example.org".getBytes());

        assertThatThrownBy(() -> parser.parse(notASpreadsheet, 1000))
                .isInstanceOf(SheetParser.UnreadableSheetException.class)
                .hasMessageContaining("Save As");
    }

    // -------------------------------------------------------------- helper

    /** Builds a real .xlsx in memory. First array is the header row. */
    private byte[] sheet(String[] headers, Object[]... dataRows) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet s = wb.createSheet("Attendees");

            Row header = s.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }

            for (int r = 0; r < dataRows.length; r++) {
                Row row = s.createRow(r + 1);
                Object[] values = dataRows[r];
                for (int c = 0; c < values.length; c++) {
                    if (values[c] instanceof Double d) {
                        row.createCell(c).setCellValue(d);
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(values[c]));
                    }
                }
            }

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Could not build the test workbook", e);
        }
    }
}
