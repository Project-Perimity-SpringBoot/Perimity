package com.perimity.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * READ ONLY. auth-service is the only service that issues a token; user-service
 * only verifies one, so this class has no issue() method and never will. If you
 * find yourself wanting to mint a token here, the answer is to call
 * POST /api/auth/login instead.
 *
 * perimity.jwt.secret MUST be byte-identical to auth-service's or every request
 * arrives as unauthenticated with no useful error - the signature simply fails.
 * Both read JWT_SECRET from the repo-root .env.
 *
 * The length check runs at startup rather than on the first request: a
 * misconfigured key should stop the service from booting, not surface as
 * "everyone is logged out" during a demo.
 */
@Service
public class JwtService {

    private static final String ISSUER = "perimity-auth";

    private final String secret;
    private SecretKey key;

    public JwtService(@Value("${perimity.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "perimity.jwt.secret is not set. Add JWT_SECRET to the repo-root .env. "
                    + "It must be the same value every Perimity service uses.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters. HS256 requires a 256-bit key.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * Throws JwtException on a bad signature, an expired token, or a wrong
     * issuer. requireIssuer is what stops a token minted by some other system
     * that happens to share our secret from being accepted here.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
