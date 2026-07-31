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
 * Builds the printable pass PDF that gets emailed on Day 9.
 *
 * Campus-agnostic by construction: no institution name, no logo, no
 * department list. Anything campus-specific is passed in, never hardcoded -
 * that rule is the reason the project can be demoed as a product rather than
 * as one college's internal tool.
 *
 * Deliberately carries no personal data. The PDF is emailed, forwarded and
 * printed; the holder's name and photo are looked up at scan time from
 * passId. A pass left on a printer should not identify anyone.
 */
@Service
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final float MARGIN = 50f;
    private static final float QR_SIZE = 260f;
    private static final float TITLE_SIZE = 20f;
    private static final float LABEL_SIZE = 11f;
    private static final float VALUE_SIZE = 13f;

    /**
     * @param qrPng the already-rendered QR image, so this class never touches the token
     */
    public byte[] render(QrGenerateRequest request, byte[] qrPng) {
        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float cursorY = pageHeight - MARGIN;

            PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrPng, "qr");

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {

                cursorY -= 30f;
                writeCentred(content, "GATE PASS",
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        TITLE_SIZE, pageWidth, cursorY);

                // The QR, centred, with real whitespace around it. ZXing's
                // margin hint is measured in modules; printers and phone
                // cameras want physical quiet space, which is this.
                cursorY -= (QR_SIZE + 40f);
                content.drawImage(qrImage, (pageWidth - QR_SIZE) / 2f, cursorY, QR_SIZE, QR_SIZE);

                cursorY -= 50f;
                cursorY = writeField(content, "PASS ID",
                        String.valueOf(request.getPassId()), MARGIN, cursorY);

                cursorY = writeField(content, "VALID FROM",
                        request.getValidFrom().format(DATE_FORMAT), MARGIN, cursorY);

                /*
                 * A standing DAILY pass has no end date. "No expiry" rather
                 * than a blank line or a far-future date: a guard reading a
                 * printed pass needs to see that the absence is deliberate,
                 * and a placeholder date would eventually arrive and start
                 * denying valid people.
                 */
                cursorY = writeField(content, "VALID TO",
                        request.getValidTo() == null
                                ? "No expiry"
                                : request.getValidTo().format(DATE_FORMAT),
                        MARGIN, cursorY);

                writeText(content, "Present this code at the gate. Do not share it.",
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE),
                        LABEL_SIZE, MARGIN, MARGIN + 20f);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render the pass PDF", ex);
        }
    }

    private float writeField(PDPageContentStream content, String label, String value,
                             float x, float y) throws IOException {

        writeText(content, label, new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                LABEL_SIZE, x, y);
        writeText(content, value, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                VALUE_SIZE, x, y - 18f);
        return y - 46f;
    }

    private void writeText(PDPageContentStream content, String text, PDType1Font font,
                           float fontSize, float x, float y) throws IOException {

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void writeCentred(PDPageContentStream content, String text, PDType1Font font,
                              float fontSize, float pageWidth, float y) throws IOException {

        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        writeText(content, text, font, fontSize, (pageWidth - textWidth) / 2f, y);
    }
}
