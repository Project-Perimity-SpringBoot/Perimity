package com.perimity.gatepass.bulk;

import com.perimity.gatepass.entity.GatePass;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * "Export attendance CSV" on Screen 12 - the registered-attendee roster.
 *
 * ==========================================================================
 *  THE SAME ESCAPING RULES AS THE BULK ERROR REPORT, FOR THE SAME REASON.
 * ==========================================================================
 *
 * It is tempting to write this with a StringBuilder and a comma, because a
 * roster of names looks harmless next to an error report. It is not. Every
 * holderName in this file arrived through a bulk spreadsheet upload, which
 * means it is attacker-influenced text, and the organiser is about to open it
 * in Excel.
 *
 * So the two formula-injection rules apply unchanged:
 *
 *   - a cell starting with = + - @ (or a tab/CR, which spreadsheets strip
 *     before deciding) is prefixed with an apostrophe so it is treated as text
 *   - quotes are doubled and the field is wrapped, per RFC 4180
 *
 * WHY THIS IS A SEPARATE CLASS FROM ErrorReportWriter rather than a second
 * method on it: they share the escaping, not the shape. Merging them would
 * mean one class that knows about both RowErrorResponse and GatePass, which
 * couples the bulk-validation vocabulary to the events vocabulary for the sake
 * of saving one small file. The escaping itself is the thing worth sharing, and
 * it is small enough that a shared private helper in each is cheaper than the
 * abstraction.
 *
 * NO EMAIL COLUMN, deliberately. gatepass-service does not store the holder's
 * email - it fetches it from auth-service at publish time and never persists
 * it. Adding one here would mean 600 internal calls to build one CSV, and it
 * would put a list of every attendee's email address behind an endpoint that
 * exists to count heads.
 */
@Component
public class AttendeeRosterWriter {

    public static final String CONTENT_TYPE = "text/csv";

    private static final String FORMULA_STARTERS = "=+-@\t\r";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] write(List<GatePass> passes) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // UTF-8 BOM, so Excel on Windows renders non-ASCII names correctly
        // rather than as mojibake.
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);

        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {

            w.write("Name,Pass ID,Status,Valid From,Valid To,Issued\r\n");

            for (GatePass p : passes) {
                w.write(csv(p.getHolderName()));
                w.write(',');
                w.write(String.valueOf(p.getId()));
                w.write(',');
                w.write(csv(p.getStatus() == null ? null : p.getStatus().name()));
                w.write(',');
                w.write(p.getValidFrom() == null ? "" : p.getValidFrom().format(DATE));
                w.write(',');
                // A standing DAILY pass has no end date. Say so in words rather
                // than leaving a blank cell that reads as missing data.
                w.write(p.getValidTo() == null ? "No end date" : p.getValidTo().format(DATE));
                w.write(',');
                w.write(p.getCreatedAt() == null ? "" : p.getCreatedAt().toLocalDate().format(DATE));
                w.write("\r\n");
            }

        } catch (IOException e) {
            throw new IllegalStateException("Could not build the attendee roster", e);
        }

        return out.toByteArray();
    }

    /** Neutralise first, then quote. Reversing the order puts the apostrophe
     *  outside the quotes, where the spreadsheet ignores it. */
    private String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        if (FORMULA_STARTERS.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        v = v.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
