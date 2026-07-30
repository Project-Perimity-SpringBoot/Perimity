package com.perimity.auth.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

/**
 * Counter-based rate limiting on Redis.
 *
 * INCR plus EXPIRE, nothing cleverer. Bucket4j would give smoother token-bucket
 * behaviour, but at this scale a fixed window is enough and it is far easier to
 * explain and reason about.
 *
 * The limit that actually matters here is OTP requests. Without it, anyone can
 * post the same address a thousand times and three things happen, in increasing
 * order of pain:
 *
 *   1. we have built an email bomb aimed at whoever they typed
 *   2. SES charges us for every send
 *   3. SES SUSPENDS the account once the bounce rate climbs - and then no
 *      visitor anywhere gets a pass email, so the whole product stops
 *
 * Per-account lockout does not help against any of that, because the attacker
 * is not attacking an account. Nor does it stop credential stuffing, where one
 * attempt each is made against a thousand different accounts and every account
 * stays under its own threshold.
 *
 * Fails OPEN. If Redis is down the service keeps working - being unable to
 * count requests is not a reason to stop letting people sign in.
 */
@Service
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return true when the action is allowed, false when the budget is spent.
     */
    public boolean allow(String bucket, String subject, int limit, Duration window) {
        if (subject == null || subject.isBlank()) {
            return true;
        }
        String key = "rl:" + bucket + ":" + subject.toLowerCase();

        try {
            ValueOperations<String, String> ops = redis.opsForValue();
            Long count = ops.increment(key);
            if (count != null && count == 1L) {
                // Only the first request in a window sets the TTL, so the window
                // starts at the first request rather than sliding forward on
                // every one - otherwise a steady attacker resets it forever.
                redis.expire(key, window);
            }
            boolean allowed = count == null || count <= limit;
            if (!allowed) {
                log.warn("Rate limit hit: bucket={} subject={} count={} limit={}",
                        bucket, subject, count, limit);
            }
            return allowed;

        } catch (RuntimeException ex) {
            log.error("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            return true;
        }
    }

    /** Seconds until the window resets, for the Retry-After hint. */
    public long secondsUntilReset(String bucket, String subject) {
        try {
            Long ttl = redis.getExpire("rl:" + bucket + ":" + subject.toLowerCase());
            return ttl == null || ttl < 0 ? 0 : ttl;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Clears a bucket. Used after a successful login so a typo does not linger. */
    public void reset(String bucket, String subject) {
        try {
            redis.delete("rl:" + bucket + ":" + subject.toLowerCase());
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
