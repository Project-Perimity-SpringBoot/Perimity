package com.perimity.guard.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP for guard-service. Two targets: qr-service and gatepass-service.
 *
 * ==========================================================================
 * WHY THE TIMEOUTS ARE SO SHORT
 * ==========================================================================
 * FR-SCAN-3 requires a scan to answer in under one second. A scan makes two
 * calls - decrypt, then pass status - so the budget is halved before anything
 * else. 400ms each leaves room for the Mongo write and still fails fast when a
 * service is down.
 *
 * gatepass-service uses 3000ms for its own internal calls, which is right for
 * approving a visitor request where a human is waiting on a form. It would be
 * wrong here: a guard holding up a queue at a gate needs an answer or an error,
 * not a three-second pause per hop.
 *
 * These numbers stop mattering on Day 11 when Redis caches the active-pass
 * lookup. Until then they are the whole latency story, so they are properties
 * rather than constants - tune without a rebuild.
 *
 * ==========================================================================
 * WHY NOT ONE CLIENT WITH THE URL PASSED IN
 * ==========================================================================
 * Two pre-built clients, each with its own baseUrl and the internal key already
 * attached as a default header. A single client taking a full URL per call is
 * one forgotten header away from sending the shared key to somewhere it should
 * not go.
 */
@Configuration
public class InternalClientsConfig {

    /** Same header name as auth, gatepass and qr. Checked - all four agree. */
    public static final String KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    RestClient qrRestClient(RestClient.Builder builder,
                            @Value("${perimity.services.qr-url}") String qrUrl,
                            @Value("${perimity.services.timeout-ms}") long timeoutMs,
                            @Value("${perimity.internal.api-key}") String apiKey) {

        return builder.clone()
                .baseUrl(qrUrl)
                .requestFactory(factory(timeoutMs))
                .defaultHeader(KEY_HEADER, apiKey)
                .build();
    }

    @Bean
    RestClient gatepassRestClient(RestClient.Builder builder,
                                  @Value("${perimity.services.gatepass-url}") String gatepassUrl,
                                  @Value("${perimity.services.timeout-ms}") long timeoutMs,
                                  @Value("${perimity.internal.api-key}") String apiKey) {

        return builder.clone()
                .baseUrl(gatepassUrl)
                .requestFactory(factory(timeoutMs))
                .defaultHeader(KEY_HEADER, apiKey)
                .build();
    }

    private SimpleClientHttpRequestFactory factory(long timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }
}
