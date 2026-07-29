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
 * Issues and reads JWTs. jjwt 0.12.x API.
 *
 * The secret comes from the JWT_SECRET environment variable with no fallback,
 * on purpose. A default value here is how the old committed secret ended up in
 * production-shaped config in the first place - if the variable is missing the
 * service must refuse to start, loudly, rather than silently signing tokens
 * with a string that is public on GitHub.
 *
 * The same secret must be set for all six services or tokens issued here will
 * not validate anywhere else.
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

    /** Everything downstream services need, so they never call back here to ask. */
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

    /** Throws JwtException on anything wrong: bad signature, expired, malformed. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer("perimity-auth")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String roleOf(Claims claims) {
        return claims.get("role", String.class);
    }
}
