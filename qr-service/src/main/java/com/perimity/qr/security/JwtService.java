package com.perimity.qr.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * VERIFY ONLY. auth-service is the only service that issues a token.
 *
 * Not a copy of auth-service's JwtService - that one has issue(), and a service
 * that can mint tokens can mint one for any role. Five services that only ever
 * read means one place to audit if a token is ever forged.
 *
 * DO NOT CONFUSE THIS WITH QrTokenService. That one handles the AES-encrypted
 * token printed inside a QR code, which identifies a PASS. This one handles the
 * JWT in the Authorization header, which identifies a PERSON. They use different
 * keys (QR_AES_KEY and JWT_SECRET), protect different things, and sharing either
 * key between them would be a serious mistake.
 *
 * Verification is local: the signature is checked against the shared secret with
 * no network call, so qr-service does not wait on auth-service and a restart of
 * auth-service does not stop pass lookups.
 *
 * The secret has NO usable default. If JWT_SECRET is missing the service refuses
 * to start, and that is correct: a service that falls back to a known string
 * will happily accept forged tokens signed with the string that is in Git.
 */
@Service
public class JwtService {

    private final String secret;
    private SecretKey key;

    public JwtService(@Value("${perimity.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "perimity.jwt.secret is not set. Add JWT_SECRET to the repo-root .env "
                    + "and make sure it reaches the JVM environment. It must be the SAME "
                    + "value as auth-service uses, or no token will validate here.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters. HS256 requires a 256-bit key. "
                    + "Got " + bytes.length + ".");
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
