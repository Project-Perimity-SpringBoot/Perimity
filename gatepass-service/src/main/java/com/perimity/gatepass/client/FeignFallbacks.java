package com.perimity.gatepass.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * What happens when a peer is unreachable.
 *
 * ===================================================================
 *  WITHOUT THESE, FEIGN BREAKS THE PLATFORM'S MOST IMPORTANT PROPERTY
 * ===================================================================
 *
 * Feign throws on any failure. With the circuit breaker enabled it wraps that
 * in "No fallback available" - which is what happened the first time this was
 * run, and it hid the real error underneath.
 *
 * Every one of these calls only ENRICHES a QR job with a name or an email. A
 * peer being down must never stop a pass being issued. Returning null here lets
 * the caller's Optional handling take over, exactly as the RestClient version
 * behaved.
 *
 * FallbackFactory rather than a plain fallback, deliberately: the factory
 * receives the Throwable, so the real cause is logged instead of being
 * swallowed into a generic message. Debugging a silent fallback is miserable.
 */
public final class FeignFallbacks {

    private FeignFallbacks() { }

    @Component
    public static class Campus implements FallbackFactory<CampusFeignClient> {
        private static final Logger log = LoggerFactory.getLogger(Campus.class);

        @Override
        public CampusFeignClient create(Throwable cause) {
            return campusId -> {
                log.warn("campus-service unreachable for campus {} - the pass will be issued "
                        + "without a campus name. {}", campusId, describe(cause));
                return null;
            };
        }
    }

    @Component
    public static class Auth implements FallbackFactory<AuthFeignClient> {
        private static final Logger log = LoggerFactory.getLogger(Auth.class);

        @Override
        public AuthFeignClient create(Throwable cause) {
            return userId -> {
                log.warn("auth-service unreachable for user {} - the pass will be generated "
                        + "but not emailed. {}", userId, describe(cause));
                return null;
            };
        }
    }

    @Component
    public static class User implements FallbackFactory<UserFeignClient> {
        private static final Logger log = LoggerFactory.getLogger(User.class);

        @Override
        public UserFeignClient create(Throwable cause) {
            return userId -> {
                log.warn("user-service unreachable for user {} - the pass PDF will have no "
                        + "photo. {}", userId, describe(cause));
                return null;
            };
        }
    }

    /** The message, and the type, because Feign's messages are often opaque alone. */
    static String describe(Throwable cause) {
        if (cause == null) {
            return "no cause reported";
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
