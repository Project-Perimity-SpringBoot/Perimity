package com.perimity.qr.service;

import com.perimity.qr.dto.QrGenerateRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * Renders the printable gate pass: one card, centred on an A4 page, with a
 * violet header band, the holder's details, a framed QR code and a footer.
 *
 * ==========================================================================
 * NOT ONE INSTITUTION NAME IN THIS FILE
 * ==========================================================================
 * Every campus-specific string arrives on the request. The previous version
 * fell back to a real college name and a real department when those fields
 * were blank, which meant a pass for a campus that had not set its name
 * printed somebody else's - and it is exactly the kind of literal the
 * branding guard-rail job exists to fail a build over. Blank now renders as
 * blank, or as a dash in the grid, which is honest and stays campus-agnostic.
 *
 * ==========================================================================
 * WHY A CARD RATHER THAN A FULL PAGE
 * ==========================================================================
 * The pass is read in two places and they want opposite things. At a gate it
 * is a phone screen held up in daylight, so the QR needs a white quiet zone
 * and high contrast around it. In an office it is printed and cut out. A
 * bordered card gives the cut line for the second without costing the first
 * anything.
 *
 * The one thing here that is not decoration: the QR sits inside a white
 * frame with a wide margin. Scanners need that quiet zone, and a code printed
 * hard against a coloured panel is the classic reason a pass "sometimes"
 * fails to scan.
 */
@Service
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /* ------------------------------------------------------------ palette */

    private static final float[] VIOLET_DARK = rgb(76, 29, 149);    // #4C1D95
    private static final float[] VIOLET_MID = rgb(109, 40, 217);    // #6D28D9
    private static final float[] VIOLET_SOFT = rgb(237, 233, 254);  // #EDE9FE
    private static final float[] PAGE_BG = rgb(245, 243, 255);      // #F5F3FF
    private static final float[] CARD_BORDER = rgb(226, 219, 248);  // #E2DBF8
    private static final float[] INK = rgb(30, 27, 75);             // #1E1B4B
    private static final float[] MUTED = rgb(107, 114, 128);        // #6B7280
    private static final float[] ON_VIOLET = rgb(255, 255, 255);
    private static final float[] ON_VIOLET_SOFT = rgb(221, 214, 254); // #DDD6FE

    /* ------------------------------------------------------------- layout */

    private static final float CARD_LEFT = 52f;
    private static final float CARD_RIGHT = 543f;
    private static final float CARD_TOP = 790f;
    private static final float CARD_BOTTOM = 90f;
    private static final float CARD_RADIUS = 18f;
    private static final float PAD = 28f;

    private static final float HEADER_HEIGHT = 96f;
    private static final float AVATAR_SIZE = 56f;
    private static final float QR_FRAME = 236f;
    private static final float QR_SIZE = 196f;

    private static final PDType1Font BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font ITALIC =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public byte[] render(QrGenerateRequest request, byte[] qrPng) {
        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float cardWidth = CARD_RIGHT - CARD_LEFT;
            float innerLeft = CARD_LEFT + PAD;
            float innerRight = CARD_RIGHT - PAD;

            PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrPng, "qr");

            boolean daily = request.getValidTo() == null;
            String passCode = "GP-" + String.format("%06d", request.getPassId());

            try (PDPageContentStream c = new PDPageContentStream(document, page)) {

                /* ---------------------------------------------- page wash */
                fill(c, PAGE_BG);
                c.addRect(0, 0, pageWidth, pageHeight);
                c.fill();

                /* --------------------------------------------------- card */
                fill(c, ON_VIOLET);
                roundedRect(c, CARD_LEFT, CARD_BOTTOM, cardWidth,
                        CARD_TOP - CARD_BOTTOM, CARD_RADIUS, true, true);
                c.fill();

                stroke(c, CARD_BORDER);
                c.setLineWidth(1f);
                roundedRect(c, CARD_LEFT, CARD_BOTTOM, cardWidth,
                        CARD_TOP - CARD_BOTTOM, CARD_RADIUS, true, true);
                c.stroke();

                /* ------------------------------------------- header band */
                float headerBottom = CARD_TOP - HEADER_HEIGHT;

                /*
                 * The gradient is painted as thin vertical strips inside a
                 * clip of the rounded-top shape. PDFBox has real shadings,
                 * but they need a pattern dictionary and a function object
                 * for what is, here, twenty lines of arithmetic - and the
                 * clip is needed either way to keep the corners round.
                 */
                c.saveGraphicsState();
                roundedRect(c, CARD_LEFT, headerBottom, cardWidth,
                        HEADER_HEIGHT, CARD_RADIUS, true, false);
                c.clip();
                int strips = 140;
                float stripWidth = cardWidth / strips + 0.6f;
                for (int i = 0; i < strips; i++) {
                    float t = (float) i / (strips - 1);
                    fill(c, lerp(VIOLET_DARK, VIOLET_MID, t));
                    c.addRect(CARD_LEFT + (cardWidth / strips) * i,
                            headerBottom, stripWidth, HEADER_HEIGHT);
                    c.fill();
                }
                c.restoreGraphicsState();

                String campusName = upperOrBlank(request.getCampusName());
                if (campusName.isEmpty()) {
                    // No campus name on the request. The band still needs a
                    // left-hand line, so the subtitle is promoted rather than
                    // a name being invented.
                    text(c, "SMART CAMPUS ACCESS", BOLD, 14f,
                            innerLeft, headerBottom + 54f, ON_VIOLET, 1.1f);
                } else {
                    text(c, fit(campusName, BOLD, 14f, 250f), BOLD, 14f,
                            innerLeft, headerBottom + 56f, ON_VIOLET, 1.1f);
                    text(c, "Smart Campus Access", REGULAR, 9.5f,
                            innerLeft, headerBottom + 38f, ON_VIOLET_SOFT, 0.4f);
                }

                textRight(c, daily ? "DAILY PASS" : "EVENT PASS", BOLD, 13f,
                        innerRight, headerBottom + 56f, ON_VIOLET, 1.4f);
                textRight(c, passCode, REGULAR, 10f,
                        innerRight, headerBottom + 38f, ON_VIOLET_SOFT, 0.8f);

                /* ------------------------------------------ holder block */
                float avatarTop = headerBottom - 18f;
                float avatarBottom = avatarTop - AVATAR_SIZE;

                fill(c, VIOLET_SOFT);
                roundedRect(c, innerLeft, avatarBottom, AVATAR_SIZE, AVATAR_SIZE, 14f, true, true);
                c.fill();

                String holderName = trimOr(request.getHolderName(), "");
                String initial = holderName.isEmpty()
                        ? "?" : holderName.substring(0, 1).toUpperCase(Locale.ROOT);
                float initialWidth = width(initial, BOLD, 24f);
                text(c, initial, BOLD, 24f,
                        innerLeft + (AVATAR_SIZE - initialWidth) / 2f,
                        avatarBottom + 19f, VIOLET_MID, 0f);

                float nameX = innerLeft + AVATAR_SIZE + 18f;
                text(c, fit(holderName.isEmpty() ? "Pass holder" : holderName,
                                BOLD, 19f, innerRight - nameX),
                        BOLD, 19f, nameX, avatarBottom + 32f, INK, 0f);
                text(c, "Show this QR at the gate", REGULAR, 10.5f,
                        nameX, avatarBottom + 14f, MUTED, 0f);

                /* ------------------------------------------ details grid */
                divider(c, innerLeft, innerRight, 600f);

                float col1 = innerLeft;
                float col2 = CARD_LEFT + 260f;

                field(c, "PASS ID", passCode, col1, 572f);
                field(c, "TYPE", daily ? "Daily - standing" : "Event pass", col2, 572f);

                field(c, "VALID FROM",
                        request.getValidFrom() == null
                                ? "Immediate" : request.getValidFrom().format(DATE_FORMAT),
                        col1, 524f);
                field(c, "VALID TO",
                        request.getValidTo() == null
                                ? "No end date" : request.getValidTo().format(DATE_FORMAT),
                        col2, 524f);

                // A visitor has no department, so a dash rather than a guess.
                field(c, "DEPARTMENT", trimOr(request.getDepartmentName(), "—"), col1, 476f);
                field(c, "GATE", "All campus gates", col2, 476f);

                divider(c, innerLeft, innerRight, 434f);

                /* ----------------------------------------------- qr code */
                float frameX = (pageWidth - QR_FRAME) / 2f;
                float frameY = 178f;

                fill(c, ON_VIOLET);
                roundedRect(c, frameX, frameY, QR_FRAME, QR_FRAME, 16f, true, true);
                c.fill();
                stroke(c, CARD_BORDER);
                c.setLineWidth(1.2f);
                roundedRect(c, frameX, frameY, QR_FRAME, QR_FRAME, 16f, true, true);
                c.stroke();

                c.drawImage(qrImage, (pageWidth - QR_SIZE) / 2f,
                        frameY + (QR_FRAME - QR_SIZE) / 2f, QR_SIZE, QR_SIZE);

                textCentre(c, "Scan at any gate. Re-issue if your profile changes.",
                        REGULAR, 10f, pageWidth, 158f, MUTED);

                /* ------------------------------------------------ footer */
                fill(c, VIOLET_SOFT);
                roundedRect(c, CARD_LEFT, CARD_BOTTOM, cardWidth, 42f,
                        CARD_RADIUS, false, true);
                c.fill();

                textCentre(c, "Perimity  ·  entry-only  ·  do not share this code",
                        ITALIC, 9f, pageWidth, CARD_BOTTOM + 16f, VIOLET_DARK);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render the pass PDF", ex);
        }
    }

    /* ------------------------------------------------------------ drawing */

    /**
     * A rectangle with optionally rounded top and bottom corners.
     *
     * The flags are what let the header band and the footer strip sit flush
     * inside the card: each is square where it meets the card body and round
     * where it meets the card edge.
     */
    private void roundedRect(PDPageContentStream c, float x, float y, float w, float h,
                             float r, boolean roundTop, boolean roundBottom) throws IOException {

        float k = r * 0.5523f; // circle-to-bezier constant
        float top = y + h;
        float right = x + w;

        c.moveTo(x, roundBottom ? y + r : y);

        if (roundTop) {
            c.lineTo(x, top - r);
            c.curveTo(x, top - r + k, x + r - k, top, x + r, top);
            c.lineTo(right - r, top);
            c.curveTo(right - r + k, top, right, top - r + k, right, top - r);
        } else {
            c.lineTo(x, top);
            c.lineTo(right, top);
        }

        if (roundBottom) {
            c.lineTo(right, y + r);
            c.curveTo(right, y + r - k, right - r + k, y, right - r, y);
            c.lineTo(x + r, y);
            c.curveTo(x + r - k, y, x, y + r - k, x, y + r);
        } else {
            c.lineTo(right, y);
            c.lineTo(x, y);
        }

        c.closePath();
    }

    /** One label-over-value pair in the details grid. */
    private void field(PDPageContentStream c, String label, String value, float x, float y)
            throws IOException {
        text(c, label, BOLD, 8f, x, y, MUTED, 1.1f);
        text(c, fit(value, BOLD, 12f, 210f), BOLD, 12f, x, y - 17f, INK, 0f);
    }

    private void divider(PDPageContentStream c, float x1, float x2, float y) throws IOException {
        stroke(c, CARD_BORDER);
        c.setLineWidth(0.9f);
        c.moveTo(x1, y);
        c.lineTo(x2, y);
        c.stroke();
    }

    /* --------------------------------------------------------------- text */

    private void text(PDPageContentStream c, String value, PDType1Font font, float size,
                      float x, float y, float[] colour, float spacing) throws IOException {
        c.beginText();
        c.setFont(font, size);
        fill(c, colour);
        c.setCharacterSpacing(spacing);
        c.newLineAtOffset(x, y);
        c.showText(value == null ? "" : value);
        c.endText();
        // Character spacing is graphics state, not a per-call argument. Left
        // set, the next unrelated string comes out letter-spaced.
        c.setCharacterSpacing(0f);
    }

    private void textRight(PDPageContentStream c, String value, PDType1Font font, float size,
                           float xRight, float y, float[] colour, float spacing) throws IOException {
        float w = width(value, font, size) + spacing * (value == null ? 0 : value.length());
        text(c, value, font, size, xRight - w, y, colour, spacing);
    }

    private void textCentre(PDPageContentStream c, String value, PDType1Font font, float size,
                            float pageWidth, float y, float[] colour) throws IOException {
        text(c, value, font, size, (pageWidth - width(value, font, size)) / 2f, y, colour, 0f);
    }

    private float width(String value, PDType1Font font, float size) {
        try {
            return font.getStringWidth(value == null ? "" : value) / 1000f * size;
        } catch (IOException ex) {
            // Standard 14 fonts have their widths built in, so this cannot
            // happen - but a metric failure must not lose the whole pass.
            return 0f;
        }
    }

    /**
     * Truncates to fit, with an ellipsis.
     *
     * PDF has no text wrapping and no overflow. A long name simply keeps
     * drawing, straight over the field beside it and off the card - so the
     * card silently becomes unreadable rather than merely clipped.
     */
    private String fit(String value, PDType1Font font, float size, float maxWidth) {
        if (value == null || value.isEmpty() || width(value, font, size) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() > 1
                && width(sb + ellipsis, font, size) > maxWidth) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb + ellipsis;
    }

    /* -------------------------------------------------------------- colour */

    private void fill(PDPageContentStream c, float[] colour) throws IOException {
        c.setNonStrokingColor(colour[0], colour[1], colour[2]);
    }

    private void stroke(PDPageContentStream c, float[] colour) throws IOException {
        c.setStrokingColor(colour[0], colour[1], colour[2]);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private static float[] lerp(float[] from, float[] to, float t) {
        return new float[]{
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t};
    }

    /* --------------------------------------------------------------- utils */

    private static String trimOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String upperOrBlank(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
