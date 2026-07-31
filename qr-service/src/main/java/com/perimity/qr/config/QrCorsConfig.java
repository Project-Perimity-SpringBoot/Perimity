package com.perimity.qr.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * qr-service is the only service with NO CORS configuration.
 *
 * The other five configure CORS inside their SecurityConfig. qr-service has no
 * spring-boot-starter-security at all — its InternalApiKeyFilter is registered
 * by hand — so there is no SecurityConfig for the CORS block to live in, and
 * nobody noticed the gap because nothing called qr-service from a browser until
 * the frontend arrived.
 *
 * Two screens break without this, and both fail the same confusing way — the
 * request never reaches the server, so the qr-service log is silent and only
 * the browser console says anything:
 *
 *   Bulk Progress   GET /api/qr/jobs/batch/{batchId}/progress
 *   Pass download   GET /api/qr/{passId}
 *
 * A CorsFilter rather than a WebMvcConfigurer, because it must run before
 * InternalApiKeyFilter — a rejected preflight is not a CORS problem the
 * browser can explain, it just says "blocked".
 *
 * PUT THIS AT:
 *   qr-service/src/main/java/com/perimity/qr/config/QrCorsConfig.java
 *
 * No other change needed. Nothing in this file affects the internal API key
 * path — preflights carry no credentials and are answered before it.
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
    public CorsFilter qrCorsFilter() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(allowedOrigins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return new CorsFilter(source);
    }
}
