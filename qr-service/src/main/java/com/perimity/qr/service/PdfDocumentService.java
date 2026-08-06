package com.perimity.qr.service;

import com.perimity.qr.dto.QrGenerateRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * Builds the printable pass PDF that matches the exact visual specification:
 * Header navy banner, holder info, 2-column key-value grid, centered QR code,
 * and footer disclaimers.
 */
@Service
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final float MARGIN = 50f;
    private static final float QR_SIZE = 220f;
    private static final float LABEL_SIZE = 9f;
    private static final float VALUE_SIZE = 12f;

    public byte[] render(QrGenerateRequest request, byte[] qrPng) {
        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float contentWidth = pageWidth - (2 * MARGIN);

            PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrPng, "qr");

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {

                // 1. TOP NAVY HEADER BANNER
                float bannerHeight = 70f;
                float bannerY = pageHeight - MARGIN - bannerHeight;

                content.setNonStrokingColor(0.11f, 0.32f, 0.49f); // #1c527e Navy
                content.addRect(MARGIN, bannerY, contentWidth, bannerHeight);
                content.fill();

                // Banner Left: Campus Name & Access Subtitle
                String campusName = (request.getCampusName() != null && !request.getCampusName().isBlank())
                        ? request.getCampusName().toUpperCase()
                        : "GATE PASS";

                writeText(content, campusName,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        14f, MARGIN + 16f, bannerY + 42f, 1f, 1f, 1f);

                writeText(content, "Smart Campus Access",
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        10f, MARGIN + 16f, bannerY + 22f, 0.82f, 0.9f, 0.98f);

                // Banner Right: Pass Type & Pass Code
                boolean isDaily = (request.getValidTo() == null);
                String passTypeLabel = isDaily ? "DAILY PASS" : "EVENT PASS";
                String passCode = "GP-" + String.format("%06d", request.getPassId());

                PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                writeRightAligned(content, passTypeLabel, boldFont, 14f, pageWidth - MARGIN - 16f, bannerY + 42f, 1f, 1f, 1f);
                writeRightAligned(content, passCode, regFont, 10f, pageWidth - MARGIN - 16f, bannerY + 22f, 0.82f, 0.9f, 0.98f);

                // 2. HOLDER PROFILE SECTION
                float cursorY = bannerY - 35f;

                // Photo placeholder square
                content.setNonStrokingColor(0.92f, 0.94f, 0.96f);
                content.addRect(MARGIN, cursorY - 45f, 45f, 45f);
                content.fill();
                content.setStrokingColor(0.85f, 0.88f, 0.90f);
                content.setLineWidth(1f);
                content.addRect(MARGIN, cursorY - 45f, 45f, 45f);
                content.stroke();

                // Holder name & subtitle next to photo
                String holderName = (request.getHolderName() != null && !request.getHolderName().isBlank())
                        ? request.getHolderName()
                        : "Campus Member";

                writeText(content, holderName, boldFont, 18f, MARGIN + 60f, cursorY - 18f, 0.1f, 0.15f, 0.2f);
                writeText(content, "Show this QR at the gate", regFont, 11f, MARGIN + 60f, cursorY - 36f, 0.45f, 0.5f, 0.55f);

                // 3. DIVIDER LINE 1
                cursorY = cursorY - 65f;
                drawLine(content, MARGIN, cursorY, pageWidth - MARGIN, cursorY);

                // 4. 2-COLUMN METADATA GRID
                cursorY = cursorY - 25f;
                float col1X = MARGIN;
                float col2X = MARGIN + 230f;

                // Row 1: PASS ID & TYPE
                drawField(content, "PASS ID", passCode, col1X, cursorY);
                String passTypeDetail = isDaily ? "Daily - standing" : "Event pass";
                drawField(content, "TYPE", passTypeDetail, col2X, cursorY);

                // Row 2: VALID FROM & VALID TO
                cursorY -= 40f;
                String validFrom = request.getValidFrom() != null ? request.getValidFrom().format(DATE_FORMAT) : "Immediate";
                String validTo = request.getValidTo() != null ? request.getValidTo().format(DATE_FORMAT) : "No end date";
                drawField(content, "VALID FROM", validFrom, col1X, cursorY);
                drawField(content, "VALID TO", validTo, col2X, cursorY);

                // Row 3: DEPARTMENT & GATE
                cursorY -= 40f;
                String dept = (request.getDepartmentName() != null && !request.getDepartmentName().isBlank())
                        ? request.getDepartmentName()
                        : "Information technology";
                drawField(content, "DEPARTMENT", dept, col1X, cursorY);
                drawField(content, "GATE", "All campus gates", col2X, cursorY);

                // 5. DIVIDER LINE 2
                cursorY = cursorY - 25f;
                drawLine(content, MARGIN, cursorY, pageWidth - MARGIN, cursorY);

                // 6. CENTERED QR CODE
                cursorY = cursorY - QR_SIZE - 25f;
                content.drawImage(qrImage, (pageWidth - QR_SIZE) / 2f, cursorY, QR_SIZE, QR_SIZE);

                // Subtitle below QR
                cursorY -= 20f;
                writeCentred(content, "Scan at any gate. Re-issue if your profile changes.",
                        regFont, 10f, pageWidth, cursorY, 0.45f, 0.5f, 0.55f);

                // 7. FOOTER
                writeCentred(content, "Perimity  ·  entry-only  ·  do not share this code",
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE),
                        9f, pageWidth, MARGIN + 10f, 0.55f, 0.6f, 0.65f);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render the pass PDF", ex);
        }
    }

    private void drawField(PDPageContentStream content, String label, String value, float x, float y) throws IOException {
        writeText(content, label, new PDType1Font(Standard14Fonts.FontName.HELVETICA), LABEL_SIZE, x, y, 0.5f, 0.55f, 0.6f);
        writeText(content, value, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), VALUE_SIZE, x, y - 14f, 0.1f, 0.15f, 0.2f);
    }

    private void drawLine(PDPageContentStream content, float x1, float y1, float x2, float y2) throws IOException {
        content.setStrokingColor(0.88f, 0.9f, 0.92f);
        content.setLineWidth(0.75f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    private void writeText(PDPageContentStream content, String text, PDType1Font font, float fontSize,
                           float x, float y, float r, float g, float b) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(r, g, b);
        content.newLineAtOffset(x, y);
        content.showText(text != null ? text : "");
        content.endText();
    }

    private void writeRightAligned(PDPageContentStream content, String text, PDType1Font font, float fontSize,
                                   float xRight, float y, float r, float g, float b) throws IOException {
        float textWidth = font.getStringWidth(text != null ? text : "") / 1000f * fontSize;
        writeText(content, text, font, fontSize, xRight - textWidth, y, r, g, b);
    }

    private void writeCentred(PDPageContentStream content, String text, PDType1Font font, float fontSize,
                              float pageWidth, float y, float r, float g, float b) throws IOException {
        float textWidth = font.getStringWidth(text != null ? text : "") / 1000f * fontSize;
        writeText(content, text, font, fontSize, (pageWidth - textWidth) / 2f, y, r, g, b);
    }
}
