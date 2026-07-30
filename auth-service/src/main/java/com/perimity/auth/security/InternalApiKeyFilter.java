package com.perimity.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.auth.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /api/internal/** with the shared INTERNAL_API_KEY.
 *
 * Mirrors qr-service's InternalApiKeyFilter (Sanjay, Day 7) on purpose - six
 * independent copies of the same idea would drift. auth-service also runs
 * JwtAuthenticationFilter for everything else, so this filter answers a
 * narrower question than in qr-service:
 *
 *   JWT filter      - which human is this, and what role do they have
 *   this filter     - is this one of our own six services
 *
 * Registered in SecurityConfig with .addFilterBefore(..., JwtAuthenticationFilter.class),
 * and /api/internal/** is listed as permitAll() in the same class - Spring
 * Security never demands a Bearer token for these paths, because a service
 * calling another service will never carry one.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/internal/";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${perimity.internal.api-key}") String apiKey,
                                ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "perimity.internal.api-key is not set. Add INTERNAL_API_KEY to the "
                    + "repo-root .env and make sure it reaches the JVM environment.");
        }
        this.expectedKey = apiKey.trim().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String presented = request.getHeader(HEADER);

        if (presented == null || !matches(presented)) {
            // 401, not 403 - the caller hasn't identified itself at all. The
            // message says nothing about which header was wrong, so a caller
            // probing for internal endpoints learns nothing from the
            // difference between a bad key and a bad URL.
            writeUnauthorized(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Constant-time comparison - avoids leaking the key one character at a time via timing. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        ApiResponse<Void> body = ApiResponse.fail("Internal authentication required", List.of());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
