package com.perimity.gatepass.security;

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
 * Guards /api/gatepass/internal/**.
 *
 * These endpoints are called by other services, not by browsers, and they carry
 * no user token. A shared secret header is the right shape here:
 *
 *   /internal/visitor-requests/{id}/verified  - auth-service marks an email verified
 *   /internal/passes/{id}/activate            - qr-service turns a pass green
 *   /internal/passes/holder/{id}/pause        - user-service holds someone's access
 *
 * Each is dangerous in a visitor's hands. Verifying your own email makes the
 * whole OTP step decorative; activating your own pending pass skips generation
 * entirely.
 *
 * The comparison is constant-time so the header cannot be guessed a character
 * at a time by measuring response times.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH = "/api/gatepass/internal/";

    private final String expectedKey;

    public InternalApiKeyFilter(@Value("${perimity.internal.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    /** Only runs on internal paths. Everything else goes straight through. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String supplied = request.getHeader(HEADER);

        if (supplied == null || !constantTimeEquals(supplied, expectedKey)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
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
