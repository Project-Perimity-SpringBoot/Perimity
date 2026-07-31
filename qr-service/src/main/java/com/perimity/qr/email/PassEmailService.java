package com.perimity.qr.email;

import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.storage.StorageService;
import com.perimity.qr.validation.ValidationPatterns;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides whether a pass email should go out, sends it, and records what
 * happened on the generation job.
 *
 * NEVER THROWS. That is the single rule this class exists to enforce.
 *
 * It is called from the queue consumer after the token, the PNG, the PDF, the
 * storage writes and the result message have all succeeded. Every one of those
 * is correct and none of them should be undone because a mail server was
 * briefly unreachable. Letting an SMTP failure propagate would retry the whole
 * job, which regenerates the QR - a second token issued to repair a mail
 * problem, which is the wrong fix applied to the wrong component.
 *
 * So failures are recorded rather than raised, and the email becomes separately
 * retryable through POST /api/qr/internal/{passId}/resend-email. That is the
 * same discipline applied to Tushar's QrResultListener: a log line is not a
 * durable record, and the row is.
 */
@Service
public class PassEmailService {

    private static final Logger log = LoggerFactory.getLogger(PassEmailService.class);

    private static final Pattern EMAIL = Pattern.compile(ValidationPatterns.EMAIL);

    /**
     * Fallbacks for when gatepass-service could not compose the wording.
     *
     * His EmailCopy always produces both, so these should never be reached in
     * practice - but a null subject makes some mail servers reject the message
     * outright, and a null body would send an email with a PDF and no
     * explanation of what it is. Campus-agnostic, like everything else here.
     */
    private static final String DEFAULT_SUBJECT = "Your gate pass";
    private static final String DEFAULT_BODY =
            "Your gate pass is attached. Show it at the gate. Do not share it.";

    private final EmailSender emailSender;
    private final StorageService storageService;
    private final GenerationJobService generationJobService;

    public PassEmailService(EmailSender emailSender,
                            StorageService storageService,
                            GenerationJobService generationJobService) {
        this.emailSender = emailSender;
        this.storageService = storageService;
        this.generationJobService = generationJobService;
    }

    /**
     * Sends the pass email for a completed job, if there is one to send.
     *
     * @param jobId  the generation_jobs row to record the outcome against
     * @param message the job as it arrived, carrying the address and the wording
     * @param pdfKey the stored PDF to attach
     */
    public void sendPassEmail(Long jobId, QrGenerationJob message, String pdfKey) {
        try {
            /*
             * Already settled means already sent, or there was never an address.
             * Checked FIRST, before any storage read or SMTP work.
             *
             * This is the guard that makes a redelivery safe. A broker
             * redelivery of a DONE job republishes its result - correct, because
             * the first result may have been lost - but it must not put a second
             * copy of the same pass in someone's inbox. Tushar's contract
             * comment says it directly: a visitor must not receive two emails.
             */
            EmailStatus current = generationJobService.emailStatusOf(jobId);
            if (current != null && current.isSettled()) {
                log.debug("Pass email for job {} is already {} - not sending again",
                        jobId, current);
                return;
            }

            String recipient = message.holderEmail();

            if (recipient == null || recipient.isBlank()) {
                /*
                 * Not a failure. gatepass-service logs a warning and publishes
                 * the job anyway when a holder has no address - a walk-in
                 * visitor added by a bulk row, for instance. The pass still
                 * exists and can be collected another way.
                 */
                log.info("Job {} (pass {}) has no recipient - pass generated but not emailed",
                        jobId, message.passId());
                generationJobService.markEmailNotRequired(jobId);
                return;
            }

            if (!EMAIL.matcher(recipient.trim()).matches()) {
                /*
                 * A malformed address will be malformed on every retry, so it is
                 * recorded as FAILED immediately rather than handed to the mail
                 * server to reject three times. The address itself is not logged
                 * - the job id is enough to find it, and the log is not the
                 * place for someone's email.
                 */
                log.warn("Job {} (pass {}) has a malformed recipient address - not sending",
                        jobId, message.passId());
                generationJobService.markEmailFailed(jobId,
                        "Recipient address is not a valid email address");
                return;
            }

            if (pdfKey == null || pdfKey.isBlank()) {
                log.warn("Job {} (pass {}) has no stored PDF - not sending", jobId, message.passId());
                generationJobService.markEmailFailed(jobId, "No PDF key on the QR record");
                return;
            }

            byte[] pdf = storageService.get(pdfKey);

            emailSender.send(new PassEmail(
                    recipient.trim(),
                    orDefault(message.emailSubject(), DEFAULT_SUBJECT),
                    orDefault(message.emailGreeting(), DEFAULT_BODY),
                    pdf));

            generationJobService.markEmailSent(jobId);

        } catch (RuntimeException ex) {
            /*
             * Catching RuntimeException rather than EmailDeliveryException
             * alone, on purpose. A missing object in storage throws
             * EntityNotFoundException, an unreadable one throws
             * UncheckedIOException, and neither should be able to escape into
             * the queue consumer and retry a job that has already succeeded.
             *
             * This is the one place in the service where a broad catch is
             * correct, and the reason is that the caller has nothing useful to
             * do with any exception from here.
             */
            log.error("Pass email failed for job {} (pass {}). The pass itself is fine - "
                            + "retry with POST /api/qr/internal/{}/resend-email",
                    jobId, message.passId(), message.passId(), ex);

            generationJobService.markEmailFailed(jobId, describe(ex));
        }
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String describe(Throwable cause) {
        Throwable root = cause.getCause() == null ? cause : cause.getCause();
        String message = root.getMessage() == null ? "no detail" : root.getMessage();
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
