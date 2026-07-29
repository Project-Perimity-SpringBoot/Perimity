package com.perimity.auth.exception;

/**
 * Too many attempts. Maps to HTTP 429.
 *
 * A distinct status matters: a client that gets 400 will usually retry, and a
 * client that gets 429 knows to back off.
 */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
