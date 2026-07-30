package com.perimity.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of the security context, so no controller writes
 * SecurityContextHolder itself.
 */
@Component
public class CurrentUser {

    public PerimityPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PerimityPrincipal p)) {
            throw new ForbiddenException("No authenticated user on this request");
        }
        return p;
    }

    public Long userId() {
        return require().userId();
    }

    public Long campusId() {
        PerimityPrincipal p = require();
        if (p.campusId() == null) {
            throw new ForbiddenException(
                    "This action needs a campus. A Super Admin must act through a campus context.");
        }
        return p.campusId();
    }

    /**
     * The check a role annotation cannot express.
     *
     * hasRole('STUDENT') says a student may call an endpoint. It cannot say
     * WHOSE record they may touch. Without this, any student could read or
     * modify another account by changing the id in the URL.
     */
    public void requireSelfOrStaff(Long targetUserId) {
        PerimityPrincipal p = require();
        if (p.isStaff() || p.userId().equals(targetUserId)) {
            return;
        }
        throw new ForbiddenException("You may only act on your own account.");
    }

    /** A Campus Admin is confined to their own campus; a Super Admin is not. */
    public void requireSameCampus(Long targetCampusId) {
        PerimityPrincipal p = require();
        if (p.isSuperAdmin()) {
            return;
        }
        if (p.campusId() == null || !p.campusId().equals(targetCampusId)) {
            throw new ForbiddenException("That record belongs to a different campus.");
        }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }
}
