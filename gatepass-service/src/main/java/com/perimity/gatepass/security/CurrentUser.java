package com.perimity.gatepass.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of the security context.
 *
 * Exists so no controller ever writes SecurityContextHolder itself. One place
 * that knows how the principal is stored means one place to change if it ever
 * moves.
 */
@Component
public class CurrentUser {

    public PerimityPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PerimityPrincipal principal)) {
            throw new AccessDeniedInThisServiceException("No authenticated user on this request");
        }
        return principal;
    }

    /** The caller's campus. Every campus-scoped read and write uses this. */
    public Long campusId() {
        PerimityPrincipal p = require();
        if (p.campusId() == null) {
            throw new AccessDeniedInThisServiceException(
                    "This action needs a campus. A Super Admin must act through a campus context.");
        }
        return p.campusId();
    }

    public Long userId() {
        return require().userId();
    }

    /**
     * A student or visitor may only touch their own records; staff may touch
     * anyone's on their campus.
     *
     * This is the check that role annotations cannot express. hasRole('STUDENT')
     * says a student may call the endpoint - it cannot say WHOSE data they may
     * read. Without this, any student could read any other student's passes
     * simply by changing the id in the URL.
     */
    public void requireSelfOrStaff(Long targetUserId) {
        PerimityPrincipal p = require();
        if (p.isStaff()) {
            return;
        }
        if (!p.userId().equals(targetUserId)) {
            throw new AccessDeniedInThisServiceException(
                    "You may only view your own records.");
        }
    }

    /** Thrown rather than Spring's AccessDeniedException so the handler can shape it. */
    public static class AccessDeniedInThisServiceException extends RuntimeException {
        public AccessDeniedInThisServiceException(String message) {
            super(message);
        }
    }
}
