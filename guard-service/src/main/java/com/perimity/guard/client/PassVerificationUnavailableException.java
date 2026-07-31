package com.perimity.guard.client;

/**
 * qr-service or gatepass-service could not be reached, or answered with an
 * error that is not about this pass.
 *
 * ==========================================================================
 * THIS IS NOT A DENIAL, AND THE DIFFERENCE IS THE POINT
 * ==========================================================================
 * FR-SCAN-10 requires the guard to be able to tell "this pass is invalid" apart
 * from "the scanner is broken". They demand opposite actions: a red card means
 * turn the person away, an outage means stop scanning and call someone.
 *
 * So this is thrown rather than converted into a RED result. Two consequences,
 * both deliberate:
 *
 *   - NO EntryLog document is written. An outage is not a scan. Recording it as
 *     a denied entry would put a refusal against a person's name for something
 *     they did not do, in the collection whose entire value is being an accurate
 *     record of what happened at the gate.
 *
 *   - it surfaces as 503, not 200 with a negative verdict. The scanner UI keys
 *     the full-screen red card off the 200 body, so a 503 cannot be mistaken for
 *     a refusal by the screen either.
 *
 * The failure is CLOSED - nobody is let through when we cannot verify. Section
 * 5.6 says a single service failing must not prevent gate scanning, and Redis on
 * Day 11 is what actually delivers that: a cached active pass will still scan
 * green while qr-service restarts. Until the cache exists, refusing loudly is
 * the honest behaviour, and much better than the alternative of waving people
 * through on a timeout.
 */
public class PassVerificationUnavailableException extends RuntimeException {

    public PassVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PassVerificationUnavailableException(String message) {
        super(message);
    }
}
