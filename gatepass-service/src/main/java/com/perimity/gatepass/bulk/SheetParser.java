package com.perimity.gatepass.bulk;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the uploaded .xlsx into ParsedRow objects. No validation here - this
 * class only answers "what does the file say".
 *
 * ==========================================================================
 *  COLUMNS ARE FOUND BY HEADER NAME, NOT BY POSITION.
 * ==========================================================================
 *
 * A faculty member who adds a "Department" column in the middle of the sheet,
 * or reorders the columns, or exports from Google Sheets with different
 * capitalisation, must not silently get 600 passes with the phone number in
 * the name field. Position-based reading fails that way SILENTLY, which is the
 * worst kind of failure - every row is "valid" and every pass is wrong.
 *
 * Header matching is case-insensitive and ignores spaces and underscores, so
 * "Email", "email", "E-Mail" and "email_address" all resolve.
 *
 * Extra columns are ignored rather than rejected. A campus that keeps its own
 * notes column in the same sheet should not have to strip it before uploading.
 */
@Component
public class SheetParser {

    private static final Logger log = LoggerFactory.getLogger(SheetParser.class);

    /**
     * Shortest alias length that may be matched as a PREFIX rather than
     * exactly. Eight is the length of "fullname" - long enough that a header
     * beginning with one of these aliases is not plausibly a different
     * question, and short enough to catch every column Google renames.
     */
    private static final int PREFIX_MIN_LENGTH = 8;

    /** The four columns the Event & Bulk design document specifies. */
    static final String COL_NAME = "name";
    static final String COL_EMAIL = "email";
    static final String COL_PHONE = "phone";
    static final String COL_PURPOSE = "purpose";

    /*
     * ======================================================================
     *  THE REST OF A GOOGLE FORM RESPONSES SHEET - ALL OPTIONAL
     * ======================================================================
     * The student intake form in user-service asks for these, and faculty
     * running an event reuse that same form rather than building a new one.
     * Recognising the columns means the details survive the upload instead of
     * being silently dropped; leaving every one of them OPTIONAL means a bare
     * name-and-email RSVP sheet still uploads.
     *
     * These names deliberately match FormColumn in user-service. They are two
     * copies because the services share no module and must be deployable
     * independently - not because the vocabularies are meant to drift. If a
     * header alias is added there, add it here too.
     */
    static final String COL_FIRST_NAME = "first name";
    static final String COL_MIDDLE_NAME = "middle name";
    static final String COL_LAST_NAME = "last name";
    static final String COL_DOB = "date of birth";
    static final String COL_GENDER = "gender";
    static final String COL_ADDRESS = "address";
    static final String COL_ROLL_NO = "roll number";
    static final String COL_DEPARTMENT = "department";
    static final String COL_PHOTO = "passport photo";

    /**
     * Alternative spellings accepted for each column. Cheap to add to, and the
     * alternative is a support conversation every time someone's export tool
     * words a header differently.
     */
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("name", COL_NAME),
            Map.entry("fullname", COL_NAME),
            Map.entry("attendeename", COL_NAME),
            Map.entry("visitorname", COL_NAME),
            Map.entry("studentname", COL_NAME),

            Map.entry("email", COL_EMAIL),
            Map.entry("emailaddress", COL_EMAIL),
            Map.entry("emailid", COL_EMAIL),
            Map.entry("mail", COL_EMAIL),

            Map.entry("phone", COL_PHONE),
            Map.entry("phoneno", COL_PHONE),
            Map.entry("phonenumber", COL_PHONE),
            Map.entry("mobile", COL_PHONE),
            Map.entry("mobileno", COL_PHONE),
            Map.entry("contact", COL_PHONE),

            Map.entry("purpose", COL_PURPOSE),
            Map.entry("reason", COL_PURPOSE),
            Map.entry("purposeofvisit", COL_PURPOSE),

            Map.entry("firstname", COL_FIRST_NAME),
            Map.entry("givenname", COL_FIRST_NAME),

            Map.entry("middlename", COL_MIDDLE_NAME),

            Map.entry("lastname", COL_LAST_NAME),
            Map.entry("surname", COL_LAST_NAME),
            Map.entry("familyname", COL_LAST_NAME),

            Map.entry("dateofbirth", COL_DOB),
            Map.entry("dob", COL_DOB),
            Map.entry("birthdate", COL_DOB),

            Map.entry("gender", COL_GENDER),
            Map.entry("sex", COL_GENDER),

            Map.entry("address", COL_ADDRESS),
            Map.entry("residentialaddress", COL_ADDRESS),
            Map.entry("homeaddress", COL_ADDRESS),

            Map.entry("rollnumber", COL_ROLL_NO),
            Map.entry("rollno", COL_ROLL_NO),
            Map.entry("roll", COL_ROLL_NO),
            Map.entry("enrollmentnumber", COL_ROLL_NO),

            Map.entry("department", COL_DEPARTMENT),
            Map.entry("branch", COL_DEPARTMENT),
            Map.entry("course", COL_DEPARTMENT),
            Map.entry("programme", COL_DEPARTMENT),
            Map.entry("program", COL_DEPARTMENT),

            Map.entry("passportphoto", COL_PHOTO),
            Map.entry("photo", COL_PHOTO),
            Map.entry("photograph", COL_PHOTO),
            Map.entry("passportsizephoto", COL_PHOTO)
    );

    /**
     * Thrown when the file cannot be read at all - not a corrupt row, but a
     * corrupt or wrong-shaped file. The batch goes to FAILED, not VALIDATED.
     */
    public static class UnreadableSheetException extends RuntimeException {
        public UnreadableSheetException(String message) {
            super(message);
        }
        public UnreadableSheetException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parses the sheet.
     *
     * @param maxRows hard stop from campus config bulk.upload.max.rows. Checked
     *                DURING the read, not after, so a hostile 5-million-row
     *                file cannot exhaust heap before anyone gets to reject it.
     */
    public List<ParsedRow> parse(InputStream in, int maxRows) {

        try (Workbook workbook = new XSSFWorkbook(in)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new UnreadableSheetException("That workbook has no sheets in it.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());

            if (header == null) {
                throw new UnreadableSheetException(
                        "The first row must be a header row naming the columns.");
            }

            Map<String, Integer> columns = mapHeaders(header);
            requireColumn(columns, COL_NAME);
            requireColumn(columns, COL_EMAIL);

            List<ParsedRow> rows = new ArrayList<>();
            int firstDataRow = header.getRowNum() + 1;

            for (int i = firstDataRow; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                // +1 because POI is 0-based and Excel's row numbers are 1-based.
                // This is the number the user will read in an error message.
                ParsedRow parsed = new ParsedRow(
                        i + 1,
                        text(row, columns.get(COL_NAME)),
                        text(row, columns.get(COL_EMAIL)),
                        text(row, columns.get(COL_PHONE)),
                        text(row, columns.get(COL_PURPOSE)),
                        new ParsedRow.Details(
                                text(row, columns.get(COL_FIRST_NAME)),
                                text(row, columns.get(COL_MIDDLE_NAME)),
                                text(row, columns.get(COL_LAST_NAME)),
                                text(row, columns.get(COL_DOB)),
                                text(row, columns.get(COL_GENDER)),
                                text(row, columns.get(COL_ADDRESS)),
                                text(row, columns.get(COL_ROLL_NO)),
                                text(row, columns.get(COL_DEPARTMENT)),
                                text(row, columns.get(COL_PHOTO))));

                // Excel routinely reports thousands of trailing rows that only
                // ever held formatting. Counting them would report "5000 rows,
                // 4400 errors" for a 600-row sheet.
                if (parsed.isEmpty()) {
                    continue;
                }

                rows.add(parsed);

                if (rows.size() > maxRows) {
                    throw new UnreadableSheetException(
                            "That sheet has more than " + maxRows + " rows, which is this "
                                    + "campus's limit for one upload. Split it and upload "
                                    + "the parts separately.");
                }
            }

            if (rows.isEmpty()) {
                throw new UnreadableSheetException(
                        "That sheet has a header row but no data rows underneath it.");
            }

            log.info("Parsed {} data row(s) from the uploaded sheet", rows.size());
            return rows;

        } catch (UnreadableSheetException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // POI throws a wide spread of runtime types for a file that is not
            // really an xlsx - a renamed .csv, an old .xls, a PDF. One message
            // for all of them; the specific POI exception means nothing to a
            // faculty member.
            throw new UnreadableSheetException(
                    "That file could not be read as an .xlsx spreadsheet. If it was saved "
                            + "as .xls or .csv, open it in Excel and use Save As > "
                            + "Excel Workbook (.xlsx).", e);
        }
    }

    // ------------------------------------------------------------- headers

    /**
     * Header text -> column index, in two passes.
     *
     * ======================================================================
     *  WHY AN EXACT PASS BEFORE A PREFIX PASS
     * ======================================================================
     * Google Forms appends its own suffix to a file-upload question, so the
     * photo column arrives as "Passport photo (File responses)". Exact
     * matching alone misses it and the photo link is silently dropped.
     *
     * Prefix matching alone is worse: "Name of your organisation" starts with
     * "name" and would claim the NAME column, putting a company in the field
     * that becomes the name printed on the pass. So EVERY header is offered an
     * exact match first, across the whole row, and only the leftovers are
     * offered a prefix match. A sheet carrying both "Full Name" and "Name of
     * your organisation" therefore resolves the real one no matter which
     * column comes first in the file.
     *
     * The prefix pass is additionally restricted to aliases of at least
     * PREFIX_MIN_LENGTH characters. "passportphoto" is long enough that a
     * header starting with it means the photo; "name", "roll" and "photo" are
     * not, and are left to exact matching only.
     */
    private Map<String, Integer> mapHeaders(Row header) {
        Map<String, Integer> found = new HashMap<>();
        Map<Integer, String> unmatched = new LinkedHashMap<>();

        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String raw = text(header, c);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalised = normalise(raw);
            String canonical = HEADER_ALIASES.get(normalised);
            if (canonical != null) {
                // putIfAbsent: if a sheet somehow has two "email" columns, the
                // first one wins rather than the last silently overwriting it.
                found.putIfAbsent(canonical, c);
            } else {
                unmatched.put(c, normalised);
            }
        }

        for (Map.Entry<Integer, String> entry : unmatched.entrySet()) {
            prefixMatch(entry.getValue())
                    .ifPresent(canonical -> found.putIfAbsent(canonical, entry.getKey()));
        }
        return found;
    }

    /** Longest alias wins, so "phonenumber" beats "phone" on the same header. */
    private Optional<String> prefixMatch(String normalisedHeader) {
        return HEADER_ALIASES.entrySet().stream()
                .filter(e -> e.getKey().length() >= PREFIX_MIN_LENGTH)
                .filter(e -> normalisedHeader.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue);
    }

    private void requireColumn(Map<String, Integer> columns, String name) {
        if (!columns.containsKey(name)) {
            throw new UnreadableSheetException(
                    "The sheet is missing a \"" + name + "\" column. The only required "
                            + "columns are name and email. Phone, purpose, first/middle/last "
                            + "name, date of birth, gender, address, roll number, department "
                            + "and passport photo are all read if present and ignored if not, "
                            + "so a Google Form export can be uploaded unchanged. Download the "
                            + "template if you are unsure of the format.");
        }
    }

    private String normalise(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // --------------------------------------------------------------- cells

    /**
     * Every cell read as trimmed text.
     *
     * The numeric branch is the one that matters. A phone number typed into a
     * General cell is stored by Excel as a double, and Java's default rendering
     * of 9123456789.0 is "9.123456789E9". BigDecimal with the scale stripped
     * gives "9123456789", which is what the person typed.
     */
    private String text(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? String.valueOf(cell.getLocalDateTimeCellValue().toLocalDate())
                    : new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                            .stripTrailingZeros().toPlainString();
            case FORMULA -> formulaText(cell);
            default -> null;
        };

        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Reads a formula cell's CACHED result rather than evaluating it.
     *
     * Evaluating arbitrary formulas from an uploaded file is untrusted-input
     * execution, and a sheet full of volatile formulas can be made slow on
     * purpose. The cached value is what the uploader saw on screen when they
     * saved, which is the honest answer to "what does this sheet say".
     */
    private String formulaText(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                        .stripTrailingZeros().toPlainString();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }
}
