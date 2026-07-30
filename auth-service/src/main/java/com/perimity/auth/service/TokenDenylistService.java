package com.perimity.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Makes logout mean something (FR-SESS-2).
 *
 * A JWT is stateless: once signed it stays valid until it expires, and this
 * service issues 24-hour tokens. "Log out" that only deletes the copy in the
 * browser leaves a working credential in the browser history, in any proxy log
 * that saw the header, and on a shared machine. For a system whose whole point
 * is controlling who gets through a gate, that is not logout.
 *
 * So a logged-out token's jti is written to Redis and the filter refuses it on
 * the next request. The key expires exactly when the token would have anyway -
 * there is no point remembering a token nobody could use.
 *
 * WHY REDIS AND NOT A TABLE: this is write-once, read-often, and every entry
 * deletes itself. A database table would need a scheduled cleanup job that
 * nobody would remember to write, and a row that outlives its own token is
 * pure cost. Redis TTL is exactly this problem.
 *
 * FAILS OPEN, and that is a deliberate trade worth being able to defend.
 * If Redis is unreachable this returns false and the token is accepted. The
 * alternative - refuse everything when the denylist cannot be read - means one
 * Redis restart locks every user out of the platform, including the guard on
 * the gate. §5.6 says a single component failing must not stop gate scanning.
 * The exposure is a token that was logged out during a Redis outage still
 * working until it expires; the cost of the other choice is the whole system
 * down. Same posture RateLimiter already takes, for the same reason.
 */
@Service
public class TokenDenylistService {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylistService.class);

    /**
     * Namespaced so the other five services can read the same keys with the
     * same prefix. They share one Redis instance, exactly as they share
     * RabbitMQ. See the note in the README about the five-line check each of
     * them needs before logout is platform-wide rather than auth-only.
     */
    private static final String PREFIX = "perimity:jwt:denied:";

    private final StringRedisTemplate redis;

    public TokenDenylistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param jti      the token's unique id claim
     * @param expiry   when that token would have expired on its own
     */
    public void deny(String jti, Instant expiry) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), expiry);
        if (remaining.isNegative() || remaining.isZero()) {
            // Already expired. Nothing to deny.
            return;
        }
        try {
            redis.opsForValue().set(PREFIX + jti, "1", remaining);
        } catch (RuntimeException ex) {
            // Logged loudly: this is the one failure mode where a user believes
            // they logged out and did not.
            log.error("Could not write token {} to the denylist - it stays valid "
                    + "until it expires: {}", jti, ex.getMessage());
        }
    }

    public boolean isDenied(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (RuntimeException ex) {
            log.error("Denylist unreachable, accepting token {}: {}", jti, ex.getMessage());
            return false;
        }
    }
}
