package com.perimity.qr.email;

/**
 * Sends one pass email.
 *
 * An interface with one SMTP implementation today, deliberately mirroring
 * StorageService. The same argument applies: Day 22 may swap the transport, and
 * that change should be one new class plus a property, with nothing in
 * PassEmailService or the queue consumer knowing which one is in use.
 *
 * ON "SEND THROUGH SES", WHICH IS WHAT DAY 9 ACTUALLY ASKS FOR
 *
 * SES is reached here over its SMTP interface rather than through the AWS SDK,
 * and that is a decision worth being able to defend rather than an omission.
 *
 * SES exposes both an HTTPS API and an SMTP endpoint; both are SES. Going over
 * SMTP means local development against MailHog and production against SES are
 * the same code path, differing only by host, port and credentials. The
 * alternative - an SDK client - cannot be exercised at all until an AWS account
 * with production access exists, which under the plan is Day 22. Code whose
 * first real execution is on deployment day is the code that breaks on
 * deployment day.
 *
 * What the SDK would buy, and what is therefore given up: a message id per
 * send, and bounce and complaint notifications through SNS. Neither is needed
 * for a demo, both would matter for a product with real deliverability
 * obligations. If that changes, a SesApiEmailSender implements this interface
 * and nothing else moves.
 */
public interface EmailSender {

    /**
     * @throws EmailDeliveryException when the message could not be handed to
     *         the mail server. Callers are expected to record the failure, not
     *         to fail the work that produced the email.
     */
    void send(PassEmail email);
}
