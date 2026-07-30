package com.perimity.qr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.qr.dto.ApiResponse;
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
 * Day 7 for qr-service is "internal-only endpoints; queue consumer needs no
 * HTTP auth". This is deliberately NOT Spring Security and does not touch
 * Omkar's JwtAuthFilter, because the two answer different questions:
 *
 *   JWT           - which human is this, and what role do they have
 *   internal key  - is this one of our own six services
 *
 * gatepass-service calling invalidate has no user and no JWT to present. Made
 * to carry one, it would need a service account, which is a login that never
 * expires and never gets rotated - strictly worse than a shared key held in
 * .env. Keeping this as a plain servlet filter also means adding
 * spring-boot-starter-security later cannot silently change the behaviour of
 * these endpoints.
 *
 * The queue consumer built on Day 8 is unaffected: a RabbitMQ message never
 * passes through the servlet container, so it never reaches this filter.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/qr/internal/";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${qr.internal.api-key}") String apiKey,
                                ObjectMapper objectMapper) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "qr.internal.api-key is not set. Add INTERNAL_API_KEY to the repo-root "
                    + ".env and make sure it reaches the JVM environment.");
        }
        this.expectedKey = apiKey.trim().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /**
     * Everything outside /api/internal/ is someone else's problem - the public
     * read endpoints, ping and Swagger are all skipped here and will be
     * covered by the shared JWT filter.
     */
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
             * keeps the distinction Omkar's Day 7 gate is stated in terms of:
             * unauthenticated is 401, wrong role is 403.
             *
             * The message says nothing about which header was wrong or whether
             * the path exists. A caller probing for internal endpoints should
             * learn nothing from the difference between a bad key and a bad URL.
             */
            writeUnauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Constant-time comparison.
     *
     * String.equals returns as soon as two bytes differ, so the time it takes
     * leaks how many leading characters were correct - enough, over many
     * requests, to recover a key one character at a time. MessageDigest.isEqual
     * always reads both arrays fully. The keys are not secret from each other's
     * services, but this endpoint is reachable from anywhere the service is,
     * and a timing-safe compare costs nothing.
     */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }

    /** Same ApiResponse shape as every other failure, so the caller parses one format. */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        ApiResponse<Void> body = ApiResponse.fail(
                "Internal authentication required", List.of());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
