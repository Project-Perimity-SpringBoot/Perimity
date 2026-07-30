package com.perimity.user.config;

import com.perimity.user.client.HttpPassPauseClient;
import com.perimity.user.client.NoOpPassPauseClient;
import com.perimity.user.client.PassPauseClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the pass-pause implementation at startup.
 *
 * Chosen here in one @Bean method rather than with two @ConditionalOnProperty
 * annotations, because @ConditionalOnProperty treats an EMPTY value as present.
 * It would have selected the HTTP client with no base URL and failed on the
 * first sensitive edit rather than at startup - the worst of both.
 *
 * DAY 12: the URL now defaults to http://localhost:8083 rather than to nothing.
 * With an empty default this method silently returned the no-op on a normal
 * developer machine, so editing a photo logged a warning and left the pass
 * ACTIVE - the exact behaviour the pause rule exists to prevent, and invisible
 * unless somebody read the log. Blanking the property still selects the no-op,
 * which is now a deliberate act rather than the default state.
 */
@Configuration
public class PassPauseClientConfig {

    @Bean
    public PassPauseClient passPauseClient(
            @Value("${perimity.services.gatepass-url}") String baseUrl,
            @Value("${perimity.services.timeout-ms}") long timeoutMs,
            @Value("${perimity.internal.api-key}") String internalApiKey) {

        if (baseUrl == null || baseUrl.isBlank()) {
            return new NoOpPassPauseClient();
        }
        return new HttpPassPauseClient(baseUrl.trim(), timeoutMs, internalApiKey);
    }
}
