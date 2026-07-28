package com.perimity.auth.dto.response;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.Role;
import java.time.LocalDateTime;

/**
 * Read model for an account.
 *
 * THE MOST IMPORTANT LINE IN THIS PACK IS THE ONE THAT ISN'T HERE:
 * there is no passwordHash field, and there must never be one. Returning the
 * entity directly from a controller would ship every bcrypt hash in the system
 * to the browser. This record is the reason that cannot happen by accident.
 *
 * failedLoginCount and lockedUntil are also absent from the public shape - they
 * tell an attacker how close they are to a lockout. locked is exposed as a
 * plain boolean instead, which is all the UI needs.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        Role role,
        Long campusId,
        boolean active,
        boolean locked,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(User e) {
        return new UserResponse(
                e.getId(),
                e.getEmail(),
                e.getName(),
                e.getPhone(),
                e.getRole(),
                e.getCampusId(),
                e.isActive(),
                e.isLockedAt(LocalDateTime.now()),
                e.isMustChangePassword(),
                e.getLastLoginAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
