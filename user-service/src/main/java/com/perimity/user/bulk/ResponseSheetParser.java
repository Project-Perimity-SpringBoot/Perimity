package com.perimity.user.bulk;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns a Google Form responses .xlsx into rows of strings.
 *
 * PARSING ONLY. Nothing here decides whether a value is acceptable - that is
 * ImportRowValidator's job, and keeping them apart means a parsing change
 * cannot quietly loosen a validation rule.
 *
 * ==========================================================================
 * EVERY CELL IS READ AS TEXT
 * ==========================================================================
 * The single most important decision in this class, for the same reason
 * gatepass-service's template writes every column as text.
 *
 * A roll number like 01234 read as a number becomes 1234 - a different student,
 * silently. A phone number becomes 9.87654321E9. Excel and POI will happily do
 * both. DataFormatter reads what the cell DISPLAYS, which is what the person
 * filling the form typed.
 *
 * The one exception is the date of birth, where the numeric form is genuinely
 * more reliable than the display text: Forms writes real dates, and the display
 * format depends on the viewer's locale, so "05/08/2004" is ambiguous and the
 * underlying serial number is not.
 */
@Component
public class ResponseSheetParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseSheetParser.class);

    /**
     * Refuses a sheet before reading it into memory. POI holds the whole
     * workbook, so a large file is a heap problem, and an intake sheet is
     * kilobytes.
     */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Enough for any real intake; a defence against a pathological file. */
    public static final int MAX_ROWS = 2000;

    /**
     * Drive links have appeared in several shapes over the years:
     *   https://drive.google.com/open?id=FILEID
     *   https://drive.google.com/file/d/FILEID/view?usp=sharing
     *
     * The id is the stable part, so it is extracted rather than the URL stored.
     */
    private static final Pattern DRIVE_ID = Pattern.compile(
            "(?:/d/|[?&]id=)([A-Za-z0-9_-]{10,})");

    private final DataFormatter formatter = new DataFormatter();

    /** Thrown when the sheet cannot be used at all. Never for one bad row. */
    public static class SheetException extends RuntimeException {
        public SheetException(String message) {
            super(message);
        }
        public SheetException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One parsed row: every expected column, as the text that was in the cell. */
    public record ParsedRow(int rowNumber, Map<FormColumn, String> values) {

        public String get(FormColumn column) {
            String value = values.get(column);
            return value == null || value.isBlank() ? null : value.trim();
        }

        /** A row where every cell is empty. Forms leaves these behind. */
        public boolean isBlank() {
            return values.values().stream().allMatch(v -> v == null || v.isBlank());
        }
    }

    public record ParseResult(List<ParsedRow> rows, List<FormColumn> missingColumns) {
        public boolean usable() {
            return missingColumns.isEmpty();
        }
    }

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SheetException("No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new SheetException(
                    "That file is larger than " + (MAX_BYTES / 1024 / 1024) + " MB. "
                            + "A responses sheet should be a few hundred kilobytes.");
        }

        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new SheetException("That workbook has no sheets in it.");
            }
            Sheet sheet = workbook.getSheetAt(0);

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new SheetException("The first row is empty, so there are no column names.");
            }

            Map<FormColumn, Integer> columnIndex = mapColumns(header);

            List<FormColumn> missing = new ArrayList<>();
            for (FormColumn column : FormColumn.values()) {
                if (column.isRequired() && !columnIndex.containsKey(column)) {
                    missing.add(column);
                }
            }
            // Reported rather than thrown, so the caller can name every missing
            // column at once. Throwing on the first would make fixing a sheet a
            // guessing game played one column per upload.
            if (!missing.isEmpty()) {
                return new ParseResult(List.of(), missing);
            }

            List<ParsedRow> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();

            for (int r = sheet.getFirstRowNum() + 1; r <= lastRow; r++) {
                if (rows.size() >= MAX_ROWS) {
                    throw new SheetException(
                            "That sheet has more than " + MAX_ROWS + " rows. "
                                    + "Split it and upload in parts.");
                }
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                Map<FormColumn, String> values = new EnumMap<>(FormColumn.class);
                for (Map.Entry<FormColumn, Integer> entry : columnIndex.entrySet()) {
                    values.put(entry.getKey(), readCell(row, entry.getValue(), entry.getKey()));
                }

                // +1 because POI is 0-based and a spreadsheet is not. An error
                // saying "row 47" has to mean the row labelled 47 on screen.
                ParsedRow parsed = new ParsedRow(r + 1, values);
                if (!parsed.isBlank()) {
                    rows.add(parsed);
                }
            }

            log.info("Parsed {} rows from {}", rows.size(), file.getOriginalFilename());
            return new ParseResult(rows, List.of());

        } catch (SheetException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new SheetException(
                    "That file could not be read. Export the responses as .xlsx and try again.", ex);
        } catch (RuntimeException ex) {
            // POI throws a variety of unchecked types for a file that is not
            // really a workbook - a .csv renamed, an .xls, a download that was
            // actually an HTML error page.
            throw new SheetException(
                    "That does not look like an .xlsx workbook. In Google Forms choose "
                            + "Responses, then the Sheets icon, then File > Download > .xlsx.", ex);
        }
    }

    /**
     * Header text to column index.
     *
     * FIRST match wins, and later duplicates are ignored. A form with both
     * "Name" and "Full name" would otherwise resolve unpredictably depending on
     * map iteration order.
     */
    private Map<FormColumn, Integer> mapColumns(Row header) {
        Map<FormColumn, Integer> index = new EnumMap<>(FormColumn.class);

        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) {
                continue;
            }
            String text = formatter.formatCellValue(cell);
            for (FormColumn column : FormColumn.values()) {
                if (!index.containsKey(column) && column.matches(text)) {
                    index.put(column, c);
                    break;
                }
            }
        }
        return index;
    }

    private String readCell(Row row, int columnIndex, FormColumn column) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        if (column == FormColumn.DATE_OF_BIRTH) {
            String iso = readDate(cell);
            if (iso != null) {
                return iso;
            }
            // Fall through: the form may have used a text question for the
            // date, in which case the displayed string is all there is.
        }

        if (column == FormColumn.PHOTO) {
            return extractDriveId(formatter.formatCellValue(cell));
        }

        return formatter.formatCellValue(cell);
    }

    /**
     * A real date cell as ISO, or null when the cell is not one.
     *
     * Returning ISO rather than the displayed text removes the day/month
     * ambiguity: "05/08/2004" is two different dates depending on where the
     * person reading it lives, and the serial number underneath is not
     * ambiguous at all.
     */
    private String readDate(Cell cell) {
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = cell.getDateCellValue().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                return date.toString();
            }
        } catch (RuntimeException ex) {
            // Not a usable date cell. The caller falls back to the text form.
            log.debug("Cell was not a readable date: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * The Drive file id out of whatever link shape the form wrote.
     *
     * Returns null rather than the raw text when nothing matches. A value that
     * is not a Drive link is not a photo, and passing it through would turn a
     * recognisable "no photo on this row" into a failed fetch later, further
     * from the cause.
     */
    private String extractDriveId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = DRIVE_ID.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }
}
