package com.perimity.guard.exception;

/**
 * A document the caller named does not exist, or belongs to another campus.
 *
 * Deliberately NOT an IllegalArgumentException: the handler maps that to 400,
 * and "there is no session with that id" is a 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, String id) {
        return new ResourceNotFoundException(what + " " + id + " was not found");
    }
}
