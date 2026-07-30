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
 * annotations, because the default value of perimity.gatepass.base-url is an
 * EMPTY STRING, and @ConditionalOnProperty treats an empty value as present.
 * That would have selected the HTTP client with no base URL and failed on the
 * first sensitive edit rather than at startup - the worst of both.
 */
@Configuration
public class PassPauseClientConfig {

    @Bean
    public PassPauseClient passPauseClient(
            @Value("${perimity.gatepass.base-url:}") String baseUrl,
            @Value("${perimity.internal.api-key:}") String internalApiKey) {

        if (baseUrl == null || baseUrl.isBlank()) {
            return new NoOpPassPauseClient();
        }
        return new HttpPassPauseClient(baseUrl.trim(), internalApiKey);
    }
}
