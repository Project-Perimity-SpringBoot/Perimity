package com.perimity.user.exception;

/**
 * The caller is authenticated, but not allowed to touch THIS record.
 *
 * Deliberately separate from Spring Security's AccessDeniedException, which
 * @PreAuthorize throws for a role mismatch. The two answer different questions
 * and the split keeps them honest:
 *
 *   @PreAuthorize  - may a STUDENT call this endpoint at all?
 *   this exception - may THIS student touch THAT profile?
 *
 * A role annotation cannot express the second one. Without it, any student
 * could read another student's government id by changing the id in the URL,
 * and every role check in the service would still pass.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
