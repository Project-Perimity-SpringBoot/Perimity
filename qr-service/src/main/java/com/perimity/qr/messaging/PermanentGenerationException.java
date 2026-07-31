package com.perimity.qr.messaging;

/**
 * A generation failure that retrying cannot fix.
 *
 * The distinction this type carries is the whole point of it. Retrying is
 * correct for a broker blip, a database that is briefly unreachable, or
 * gatepass-service restarting - the same message will succeed in two seconds.
 * Retrying is wrong for a pass that was revoked while generation was queued,
 * because that will be just as revoked on the third attempt; all three
 * attempts do is delay the FAILED row by ten seconds and put three identical
 * stack traces in the log.
 *
 * Anything thrown as this type goes straight to the dead-letter queue with the
 * job marked FAILED. Anything else is treated as transient and retried.
 */
public class PermanentGenerationException extends RuntimeException {

    public PermanentGenerationException(String message) {
        super(message);
    }

    public PermanentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
