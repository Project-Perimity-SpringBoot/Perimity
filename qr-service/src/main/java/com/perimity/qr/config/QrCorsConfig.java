package com.perimity.qr.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for qr-service.
 *
 * Two screens depend on it, and both fail the same confusing way without it -
 * the request never reaches the server, so the qr-service log is silent and
 * only the browser console says anything:
 *
 *   Bulk progress   GET /api/qr/jobs/batch/{batchId}/progress
 *   Pass download   GET /api/qr/{passId}
 *
 * ======================================================================
 *  NOW A CorsConfigurationSource, NOT A STANDALONE CorsFilter
 * ======================================================================
 * This started life as a hand-registered CorsFilter, because qr-service had no
 * SecurityConfig for a CORS block to live in - it had no spring-boot-starter
 * -security at all. It has one now, so the bean below is consumed by
 * SecurityConfig exactly the way the other five services do it.
 *
 * That is not cosmetic. A standalone CorsFilter bean is auto-registered at
 * LOWEST_PRECEDENCE, which puts it AFTER the Spring Security chain - so a
 * preflight would be rejected by security before any CORS header was written,
 * and the browser would report a bare "blocked" with no clue why. Handing the
 * source to Spring Security instead puts CORS at the front of the chain, which
 * is the only position where it works. Registering both would write duplicate
 * Access-Control-Allow-Origin headers, which browsers reject outright.
 *
 * Preflights are still answered before InternalApiKeyFilter, which is what the
 * original note was protecting: an OPTIONS request carries no credentials and
 * must never be asked for an API key.
 */
@Configuration
public class QrCorsConfig {

    private final List<String> allowedOrigins;

    public QrCorsConfig(
            @Value("${perimity.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(allowedOrigins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
