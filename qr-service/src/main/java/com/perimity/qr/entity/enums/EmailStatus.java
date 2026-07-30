package com.perimity.qr.entity.enums;

/**
 * Whether the pass email for one generation job has gone out.
 *
 * Tracked on the job row rather than inferred from a log line, for the same
 * reason the generation outcome is: an email that silently failed to send is a
 * visitor standing at a gate with nothing on their phone, and "grep six
 * services" is not an answer anyone will reach for on demo day.
 *
 * PENDING      - generation finished, the email has not been attempted yet.
 * SENT         - the SMTP server accepted the message.
 * FAILED       - the send was attempted and rejected. Retryable by hand.
 * NO_RECIPIENT - there is no address to send to. Not a failure.
 *
 * NO_RECIPIENT is deliberately separate from FAILED. A bulk upload row for a
 * walk-in visitor with no email is a normal, expected case - gatepass-service
 * logs a warning and publishes the job anyway - and counting those as failures
 * would put a red number on the Bulk Progress screen for something nobody needs
 * to act on. A retry sweep must skip them; a retry sweep over FAILED is
 * exactly right.
 */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED,
    NO_RECIPIENT;

    /** SENT and NO_RECIPIENT both mean "nothing more to do here". */
    public boolean isSettled() {
        return this == SENT || this == NO_RECIPIENT;
    }
}
