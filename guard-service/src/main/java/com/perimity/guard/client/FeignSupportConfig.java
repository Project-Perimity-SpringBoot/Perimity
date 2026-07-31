package com.perimity.guard.client;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Feign setup for guard-service.
 *
 * Two things every peer call needs.
 *
 * 1. THE INTERNAL API KEY. Every /internal/** endpoint on every service is
 *    guarded by X-Internal-Api-Key. A RequestInterceptor attaches it once here,
 *    rather than each client remembering. Forgetting it on one client would
 *    produce a 401 that looks like a Eureka problem and is not.
 *
 * 2. RETRY OFF. Feign's default Retryer re-sends on connection failure. That is
 *    wrong for us: a scan must answer in about a second, and a peer that is
 *    down should degrade immediately rather than after three attempts. The
 *    fallbacks already handle absence gracefully - retrying just makes the
 *    guard wait longer for the same answer.
 */
@Configuration
public class FeignSupportConfig {

    @Bean
    public RequestInterceptor internalApiKeyInterceptor(
            @Value("${perimity.internal.api-key}") String apiKey) {
        return template -> template.header("X-Internal-Api-Key", apiKey);
    }

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
