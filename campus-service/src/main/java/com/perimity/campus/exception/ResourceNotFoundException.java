package com.perimity.campus.exception;

/**
 * A row the caller named does not exist.
 *
 * Deliberately NOT an IllegalArgumentException: the handler maps that to 400,
 * and "there is no campus 99" is a 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Long id) {
        return new ResourceNotFoundException(what + " " + id + " was not found");
    }
}
