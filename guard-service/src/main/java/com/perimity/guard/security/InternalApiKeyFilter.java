package com.perimity.guard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.guard.dto.ApiResponse;
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
 * Adapted from Sanjay's qr-service filter, which is where this pattern started.
 *
 * Deliberately NOT Spring Security, and it does not touch the JWT filter,
 * because the two answer different questions:
 *
 *     JWT           - which human is this, and what role do they have
 *     internal key  - is this one of our own six services
 *
 * A service calling another service has no human and no JWT to present. Made to
 * carry one, it would need a service account - a login that never expires and
 * never gets rotated, which is strictly worse than a shared key in .env.
 *
 * Keeping this outside Spring Security also means the matcher
 * .requestMatchers("/api/internal/**").permitAll() in SecurityConfig is safe:
 * permitAll there means "no JWT required", not "no authentication required".
 * Delete this filter and the prefix really does become public.
 *
 * The Day 8 RabbitMQ consumer is unaffected: a queue message never passes
 * through the servlet container, so it never reaches this filter.
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
                    + "repo-root .env and make sure it reaches the JVM environment. It must "
                    + "be the SAME value in all six services.");
        }
        this.expectedKey = apiKey.trim().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /** Everything outside /api/internal/ is the JWT filter's problem, not this one. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String presented = request.getHeader(HEADER);

        if (presented == null || !matches(presented)) {
            /*
             * 401, not 403. 403 means "we know who you are and you may not do
             * this"; here the caller has not identified itself at all. It also
             * keeps the Day 7 gate wording exact: unauthenticated is 401, wrong
             * role is 403.
             *
             * The message names neither the header nor the path. Someone probing
             * for internal endpoints should learn nothing from the difference
             * between a bad key and a URL that does not exist.
             */
            writeUnauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Constant-time comparison.
     *
     * String.equals returns as soon as two bytes differ, so how long it takes
     * leaks how many leading characters were correct - enough, over many
     * requests, to recover a key one character at a time.
     * MessageDigest.isEqual always reads both arrays fully.
     */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }

    /** Same ApiResponse shape as every other failure, so callers parse one format. */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        ApiResponse<Void> body = ApiResponse.fail("Internal authentication required", List.of());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
