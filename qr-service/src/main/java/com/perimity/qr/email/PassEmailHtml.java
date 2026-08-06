package com.perimity.qr.email;

/**
 * Wraps gatepass-service's wording in an HTML email that matches the pass.
 *
 * ==========================================================================
 * THIS CLASS CHOOSES NO WORDS
 * ==========================================================================
 * Every sentence a recipient reads still comes from gatepass-service, on the
 * queue, as QrGenerationJob.emailGreeting. That split is deliberate and this
 * does not touch it: gatepass knows whether the pass is daily or for an event
 * and what the campus is called, qr-service knows how to render. All that
 * happens here is markup wrapped around copy written elsewhere.
 *
 * It follows that there is no institution name in this file either, and no
 * sentence that assumes one. The two fixed lines - the header and the
 * security note - are true of every pass this system will ever issue.
 *
 * ==========================================================================
 * WHY THE HTML LOOKS LIKE 2004
 * ==========================================================================
 * Tables, inline styles, no flexbox, no external stylesheet. Not by
 * preference: Outlook renders through Word's engine, Gmail strips <style>
 * blocks in some clients, and a layout built the modern way collapses into a
 * column of unstyled text in exactly the inboxes a campus actually uses.
 *
 * The plain-text version is not a fallback nobody sees. It is what a screen
 * reader and a text-only client get, and it is what shows in notification
 * previews - so it stays the greeting as written, unaltered.
 */
final class PassEmailHtml {

    private static final String VIOLET_DARK = "#4C1D95";
    private static final String VIOLET_MID = "#6D28D9";
    private static final String VIOLET_SOFT = "#EDE9FE";
    private static final String PAGE_BG = "#F5F3FF";
    private static final String INK = "#1E1B4B";
    private static final String MUTED = "#6B7280";
    private static final String BORDER = "#E2DBF8";

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private PassEmailHtml() {
    }

    /**
     * @param greeting the plain-text body composed by gatepass-service
     * @return a complete HTML document carrying the same words
     */
    static String render(String greeting) {
        StringBuilder html = new StringBuilder(4096);

        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("</head>")
            .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG)
            .append(";\">")

            // Outer wash. A background on <body> alone is ignored by several
            // clients, so the colour is repeated on a full-width table.
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"background:").append(PAGE_BG).append(";padding:32px 12px;\"><tr><td align=\"center\">")

            // The card.
            .append("<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"width:560px;max-width:100%;background:#FFFFFF;border:1px solid ")
            .append(BORDER).append(";border-radius:16px;overflow:hidden;\">")

            // Header band. background-color first, then the gradient - a
            // client that cannot do gradients keeps a solid violet rather
            // than white text on white.
            .append("<tr><td style=\"background-color:").append(VIOLET_DARK)
            .append(";background-image:linear-gradient(90deg,").append(VIOLET_DARK)
            .append(" 0%,").append(VIOLET_MID).append(" 100%);padding:28px 32px;\">")
            .append("<div style=\"font-family:").append(FONT)
            .append(";font-size:17px;font-weight:700;color:#FFFFFF;letter-spacing:.5px;\">")
            .append("Your gate pass</div>")
            .append("<div style=\"font-family:").append(FONT)
            .append(";font-size:13px;color:#DDD6FE;padding-top:4px;\">Smart Campus Access</div>")
            .append("</td></tr>")

            // The greeting, exactly as gatepass-service wrote it.
            .append("<tr><td style=\"padding:32px 32px 8px 32px;font-family:").append(FONT)
            .append(";font-size:15px;line-height:1.65;color:").append(INK).append(";\">")
            .append(paragraphs(greeting))
            .append("</td></tr>")

            // Attachment cue. People miss paperclips; they do not miss a
            // panel that names the file.
            .append("<tr><td style=\"padding:8px 32px 0 32px;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"background:").append(VIOLET_SOFT).append(";border-radius:12px;\">")
            .append("<tr><td style=\"padding:16px 20px;font-family:").append(FONT)
            .append(";font-size:14px;color:").append(VIOLET_DARK).append(";\">")
            .append("<strong>gate-pass.pdf</strong> is attached to this email.<br>")
            .append("<span style=\"color:").append(MUTED)
            .append(";font-size:13px;\">Open it and show the QR code at the gate. ")
            .append("You can print it or keep it on your phone.</span>")
            .append("</td></tr></table></td></tr>")

            .append("<tr><td style=\"padding:24px 32px 0 32px;\">")
            .append("<div style=\"border-top:1px solid ").append(BORDER).append(";\"></div></td></tr>")

            .append("<tr><td style=\"padding:16px 32px 28px 32px;font-family:").append(FONT)
            .append(";font-size:13px;line-height:1.6;color:").append(MUTED).append(";\">")
            .append("This pass is issued to you alone. Do not forward this email or share the ")
            .append("QR code - anyone holding it can present it at a gate.")
            .append("</td></tr>")

            .append("<tr><td style=\"background:").append(VIOLET_SOFT)
            .append(";padding:14px 32px;text-align:center;font-family:").append(FONT)
            .append(";font-size:12px;color:").append(VIOLET_DARK).append(";\">")
            .append("Perimity &middot; entry-only &middot; automated message, please do not reply")
            .append("</td></tr>")

            .append("</table></td></tr></table></body></html>");

        return html.toString();
    }

    /**
     * Turns the plain-text greeting into paragraphs.
     *
     * Escaped first, then split - in that order, and it matters. The greeting
     * carries a name that came from a spreadsheet a student filled in, and a
     * name containing a "<" would otherwise put attacker-controlled markup in
     * an email the campus sent. Escaping after splitting would escape the
     * tags this method just added.
     */
    private static String paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String escaped = escape(text.strip()).replace("\r\n", "\n").replace("\r", "\n");

        StringBuilder out = new StringBuilder(escaped.length() + 128);
        for (String block : escaped.split("\n{2,}")) {
            String paragraph = block.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            out.append("<p style=\"margin:0 0 14px 0;\">")
               .append(paragraph.replace("\n", "<br>"))
               .append("</p>");
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
