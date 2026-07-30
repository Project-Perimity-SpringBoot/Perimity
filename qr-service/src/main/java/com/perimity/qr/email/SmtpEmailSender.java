package com.perimity.qr.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the pass email over SMTP, with the PDF attached.
 *
 * The same class serves MailHog on a laptop and SES in production - only host,
 * port and credentials differ, and those live in properties. See EmailSender's
 * Javadoc for why that is the deliberate choice over the AWS SDK.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    /**
     * The filename the recipient sees.
     *
     * Neutral on purpose. No institution name, no campus code, no holder name -
     * the Day 21 guard-rail job fails a build containing any institution name,
     * and an attachment filename is exactly the kind of string that gets one
     * hardcoded. It is also the only part of this email qr-service chooses, so
     * it is the only part that could go wrong here.
     */
    private static final String ATTACHMENT_NAME = "gate-pass.pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${qr.email.from}") String fromAddress,
                           @Value("${qr.email.from-name:Perimity}") String fromName) {

        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                    "qr.email.from is not set. Every pass email would be rejected by the "
                    + "mail server for having no sender. Set MAIL_FROM in the repo-root .env "
                    + "- on SES it must be a verified identity.");
        }

        this.mailSender = mailSender;
        this.fromAddress = fromAddress.trim();
        this.fromName = fromName;
    }

    @Override
    public void send(PassEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            // true = multipart, which is what makes an attachment possible at
            // all. A plain MimeMessageHelper(message) silently produces a
            // body-only email and the PDF is simply absent - no error, no
            // attachment, and nobody notices until a visitor says the email
            // was empty.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(email.to());
            helper.setSubject(email.subject());

            /*
             * Plain text, not HTML.
             *
             * gatepass-service composes the greeting with real newlines
             * ("Hi X,\n\nWelcome to..."). Sent as HTML those collapse into one
             * run-on paragraph, so it would need converting to <br> tags and
             * escaping - which puts presentation logic in the service that was
             * specifically kept out of the wording business. Plain text renders
             * his copy exactly as he wrote it.
             */
            helper.setText(email.body(), false);

            helper.addAttachment(ATTACHMENT_NAME,
                    new ByteArrayResource(email.pdf()), PDF_CONTENT_TYPE);

            mailSender.send(message);

            // The address is not logged. It is personal data, the log is not,
            // and there is nothing this line could tell you that the job row
            // does not already record against a pass id.
            log.info("Pass email sent, subject \"{}\", {} bytes attached",
                    email.subject(), email.pdf().length);

        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new EmailDeliveryException("Could not build the pass email", ex);

        } catch (MailException ex) {
            // Spring's own hierarchy: connection refused, authentication
            // rejected, recipient refused. All the same to the caller - the
            // message did not go.
            throw new EmailDeliveryException("Mail server refused the pass email", ex);
        }
    }
}
