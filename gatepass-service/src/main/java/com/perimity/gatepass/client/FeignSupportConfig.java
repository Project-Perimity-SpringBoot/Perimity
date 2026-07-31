package com.perimity.gatepass.client;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Feign setup for gatepass-service.
 *
 * Attaches X-Internal-Api-Key to every peer call and disables Feign's default
 * retry. These calls only enrich a QR job with a name and an email; retrying
 * three times just delays a pass being issued when a peer is restarting.
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
