package com.perimity.auth.dto.response;

import java.time.LocalDateTime;

/**
 * What a successful login or OTP verification returns.
 *
 * mustChangePassword is surfaced at the top level so the React shell can route
 * a seeded or admin-created account straight to the change-password screen
 * without digging into the user object.
 *
 * There is no refresh token in v1: the JWT lives 24 hours (perimity.jwt.expiry-hours)
 * and the user signs in again. Adding refresh tokens means adding revocation,
 * which is a bigger piece of work than it looks.
 */
public record AuthResponse(
        String token,
        String tokenType,
        LocalDateTime expiresAt,
        boolean mustChangePassword,
        UserResponse user
) {

    public static AuthResponse of(String token, LocalDateTime expiresAt, UserResponse user) {
        return new AuthResponse(
                token,
                "Bearer",
                expiresAt,
                user != null && user.mustChangePassword(),
                user
        );
    }
}
