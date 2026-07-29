package com.perimity.auth.security;

import com.perimity.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies JWTs. auth-service is the ONLY service that issues; the
 * other five only read.
 *
 * THE CLAIM CONTRACT - the other five services depend on these exact names:
 *
 *     sub        user id, as a string
 *     email      login email
 *     name       display name
 *     role       SUPER_ADMIN | CAMPUS_ADMIN | FACULTY | STUDENT | GUARD | VISITOR
 *     campusId   null for SUPER_ADMIN only
 *     iss        "perimity-auth"
 *
 * Renaming any of these breaks every other service silently - they will simply
 * see a null and treat the caller as unauthenticated. Announce a change before
 * making it.
 *
 * The secret has no default. If JWT_SECRET is missing the service refuses to
 * start, which is correct: a service that falls back to a known string will
 * happily accept forged tokens.
 */
@Service
public class JwtService {

    private final String secret;
    private final long expiryHours;
    private SecretKey key;

    public JwtService(@Value("${perimity.jwt.secret}") String secret,
                      @Value("${perimity.jwt.expiry-hours}") long expiryHours) {
        this.secret = secret;
        this.expiryHours = expiryHours;
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

    public String issue(User user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiryHours * 3_600_000L);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("campusId", user.getCampusId())
                .issuer("perimity-auth")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public LocalDateTime expiryOf(String token) {
        return parse(token).getExpiration()
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
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
