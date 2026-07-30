package com.perimity.qr.email;

/**
 * The mail server would not take the message.
 *
 * Deliberately NOT a subclass of PermanentGenerationException, and deliberately
 * never thrown out of PassEmailService.
 *
 * A failed email must not fail the generation job. By the time this can be
 * thrown the token exists, the PNG and the PDF are in storage, and the pass is
 * on its way to ACTIVE - all of which is correct and none of which should be
 * undone because an SMTP server was briefly unreachable. Retrying generation
 * would issue a second QR to fix a mail problem, which is the wrong repair for
 * the wrong component.
 *
 * So the caller catches this, records EmailStatus.FAILED with the reason, and
 * lets the job finish DONE. The email is then retryable on its own through
 * POST /api/qr/internal/{passId}/resend-email, without regenerating anything.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailDeliveryException(String message) {
        super(message);
    }
}
