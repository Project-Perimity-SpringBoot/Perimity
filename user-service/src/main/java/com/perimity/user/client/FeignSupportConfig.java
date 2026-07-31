package com.perimity.user.client;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Feign setup for user-service.
 *
 * The interceptor attaches X-Internal-Api-Key to every peer call, so no client
 * has to remember. Retry is off: pausing a pass is not urgent enough to hold a
 * request thread through three attempts, and the fallback already handles a
 * peer being down.
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
