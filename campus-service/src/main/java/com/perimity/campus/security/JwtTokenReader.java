package com.perimity.campus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies tokens. It does not issue them - only auth-service does that.
 *
 * The secret has no default. If JWT_SECRET is missing the service refuses to
 * start, which is the correct behaviour: a service that silently falls back to
 * a known string will happily accept forged tokens.
 */
@Component
public class JwtTokenReader {

    private final String secret;
    private SecretKey key;

    public JwtTokenReader(@Value("${perimity.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters. HS256 requires a 256-bit key.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** Throws JwtException on a bad signature, an expired token, or a wrong issuer. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer("perimity-auth")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
