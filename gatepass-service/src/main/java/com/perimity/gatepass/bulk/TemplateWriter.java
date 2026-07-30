package com.perimity.gatepass.bulk;

import com.perimity.gatepass.entity.enums.PassType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Builds the .xlsx template the uploader downloads before filling in a batch.
 *
 * The roadmap says ship this on the same day as the engine, not later, and the
 * reason is worth stating: without a template the first real upload is a guess
 * at the column names, the guess is wrong, and the error report says "missing
 * email column" for a sheet that has an "E-mail ID" column. Shipping the
 * template is cheaper than the support conversation.
 *
 * ALL COLUMNS ARE FORMATTED AS TEXT. This is the single most important line in
 * this class. Phone numbers in a General-formatted column are stored by Excel
 * as doubles, so 9123456789 comes back from POI as 9.123456789E9, and a leading
 * zero on a landline is silently deleted before the file is ever uploaded.
 * SheetParser defends against this on the way in; the template prevents it at
 * source.
 *
 * Campus-agnostic: no institution name, and the sample row uses example.com.
 */
@Component
public class TemplateWriter {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public String filenameFor(PassType passType) {
        return passType == PassType.EVENT
                ? "perimity-event-visitors-template.xlsx"
                : "perimity-student-onboarding-template.xlsx";
    }

    public byte[] write(PassType passType) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(passType == PassType.EVENT ? "Attendees" : "Students");

            CellStyle headerStyle = headerStyle(wb);
            CellStyle textStyle = textStyle(wb);
            CellStyle noteStyle = noteStyle(wb);

            // Force every data column to Text for the whole column, not just
            // the sample row. A user pasting 600 rows in gets Text too.
            for (int c = 0; c <= 3; c++) {
                sheet.setDefaultColumnStyle(c, textStyle);
            }

            String[] headers = {"name", "email", "phone", "purpose"};
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            Row sample = sheet.createRow(1);
            write(sample, 0, "Asha Menon", textStyle);
            write(sample, 1, "asha.menon@example.com", textStyle);
            write(sample, 2, "9876543210", textStyle);
            write(sample, 3, passType == PassType.EVENT
                    ? "Attending the programme" : "Student onboarding", textStyle);

            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
                // autoSizeColumn measures the sample row, which is short. A
                // floor keeps the header readable.
                sheet.setColumnWidth(c, Math.max(sheet.getColumnWidth(c), 6000));
            }

            Row blank = sheet.createRow(3);
            Cell note = blank.createCell(0);
            note.setCellValue(notesFor(passType));
            note.setCellStyle(noteStyle);

            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Could not build the upload template", e);
        }
    }

    private String notesFor(PassType passType) {
        String common = "Delete this note and the sample row before uploading.  "
                + "name and email are required; phone and purpose are optional.  "
                + "One person per row, and each email may appear only once.";

        return passType == PassType.EVENT
                ? common + "  Do NOT add a date column - the event's own dates apply to "
                        + "every row in the batch."
                : common + "  Student passes have no end date, so no dates are needed here.";
    }

    private void write(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setBorderBottom(BorderStyle.THIN);
        s.setDataFormat(textFormat(wb));
        return s;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(textFormat(wb));
        return s;
    }

    private CellStyle noteStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        return s;
    }

    /** "@" is Excel's format code for Text. */
    private short textFormat(Workbook wb) {
        return wb.createDataFormat().getFormat("@");
    }
}
