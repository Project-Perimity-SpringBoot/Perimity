package com.perimity.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /api/user/internal/**. Same shape as the filters in gatepass-service
 * and campus-service, so all six services behave identically here.
 *
 * WHY A SHARED KEY AND NOT A JWT
 * gatepass-service calls the profile summary while ISSUING a pass. There is no
 * human on that request - it may even come from a queue consumer - so there is
 * no token to forward. The two mechanisms answer different questions:
 *
 *   JWT           which human is this, and what role do they have
 *   internal key  is this one of our own six services
 *
 * Making the caller carry a JWT instead would mean inventing a service account:
 * a login that never expires and never gets rotated, which is strictly worse
 * than a key in .env.
 *
 * The comparison is constant-time. String.equals returns as soon as two bytes
 * differ, so the time it takes leaks how many leading characters were right -
 * enough, over many requests, to recover the key one character at a time.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH = "/api/user/internal/";

    private final String expectedKey;

    public InternalApiKeyFilter(@Value("${perimity.internal.api-key}") String expectedKey) {
        /*
         * DAY 12: fail at STARTUP, not on the first call.
         *
         * This used to accept a blank key and refuse every internal request at
         * runtime instead. That is safe - it never let anyone in - but it is
         * unreadable in an integration pass: gatepass and guard both get a 401,
         * their clients swallow it, and the visible symptom is a pass with no
         * photo and a scanner with a blank card. Nobody looking at those would
         * guess the cause was a missing line in .env.
         *
         * Refusing to start names the actual problem in one line, and matches
         * auth-service, gatepass-service and campus-service, which all read
         * ${INTERNAL_API_KEY} with no default. qr-service does the same check
         * in its own filter constructor.
         */
        if (expectedKey == null || expectedKey.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY is not set. Add it to the repo-root .env - the same "
                    + "value in all six services - or gatepass-service and guard-service "
                    + "cannot read profiles from this one.");
        }
        this.expectedKey = expectedKey;
    }

    /** Only runs on internal paths. Everything else goes straight through to the JWT filter. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String supplied = request.getHeader(HEADER);

        // The key cannot be blank here - the constructor refuses to build.
        if (supplied == null || !constantTimeEquals(supplied, expectedKey)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            // Says nothing about which header was wrong or whether the path
            // exists. A caller probing for internal endpoints should learn
            // nothing from the difference between a bad key and a bad URL.
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Missing or invalid internal API key\","
                            + "\"data\":null,\"errors\":[]}");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
