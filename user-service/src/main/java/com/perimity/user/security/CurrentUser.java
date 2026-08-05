package com.perimity.user.security;

import com.perimity.user.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of the security context, so no controller or service
 * writes SecurityContextHolder itself.
 *
 * Copied from auth-service, with two additions this service needs:
 * resolveCampusForListing and requireAdministrative.
 *
 * EVERY tenant-scoped value in user-service comes from here and never from a
 * request parameter. Before Day 7, campusId arrived as ?campusId=1 - which
 * meant GET /api/user/students?campusId=2 returned another campus's roll
 * numbers to anyone who tried it. That parameter is gone.
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

    /** The caller's own campus. A Super Admin has none, so this refuses them. */
    public Long campusId() {
        PerimityPrincipal p = require();
        if (p.campusId() == null) {
            throw new ForbiddenException(
                    "This action needs a campus. A Super Admin must name one with ?campusId=.");
        }
        return p.campusId();
    }

    /**
     * Which campus a list endpoint should read.
     *
     * A Super Admin has no campus of their own, so they must name one. Everyone
     * else gets their own and may not ask for another - passing someone else's
     * id is refused rather than quietly ignored, because silently returning the
     * wrong campus's data is worse than an error.
     */
    public Long resolveCampusForListing(Long requested) {
        PerimityPrincipal p = require();

        if (p.isSuperAdmin()) {
            if (requested == null) {
                throw new IllegalArgumentException(
                        "A Super Admin must name a campus: add ?campusId= to this request.");
            }
            return requested;
        }

        if (requested != null && !requested.equals(p.campusId())) {
            throw new ForbiddenException("That campus is not yours.");
        }
        return campusId();
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
        throw new ForbiddenException("You may only act on your own records.");
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

    /**
     * Verifying someone's identity document is an administrative act, not a
     * teaching one. Faculty approve visits; they do not approve ID proofs.
     */
    public void requireAdministrative() {
        if (!require().isAdministrative()) {
            throw new ForbiddenException("Only a Campus Admin or Super Admin may do this.");
        }
    }

    /**
     * Who may accept or refuse a student's self-declared details.
     *
     * DELIBERATELY WIDER than requireAdministrative, and the difference is the
     * point. Verifying a government ID proof is an administrative act, so that
     * one excludes faculty. Checking that a student has typed their own name,
     * date of birth and phone number correctly is a teaching-side act: faculty
     * are the ones who know their students and who already create their
     * accounts, so they are the ones who can spot a wrong entry.
     *
     * Admins are included because a campus with no faculty on duty still needs
     * somebody able to clear the queue.
     *
     * Guards, students and visitors are excluded. A student who could approve
     * their own details would make the whole status meaningless.
     */
    public void requireProfileReviewer() {
        if (!require().isStaff()) {
            throw new ForbiddenException(
                    "Only faculty or an admin may decide on a student's details.");
        }
    }

    /**
     * True when the caller may see another person's record. Used to decide
     * between 403 and a deliberately vague 404, never to grant access.
     */
    public boolean canSeeCampus(Long targetCampusId) {
        PerimityPrincipal p = require();
        return p.isSuperAdmin() || (p.campusId() != null && p.campusId().equals(targetCampusId));
    }
}
