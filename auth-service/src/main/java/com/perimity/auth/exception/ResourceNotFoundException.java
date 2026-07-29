package com.perimity.auth.exception;

/** A row the caller named does not exist. Maps to 404, not 400. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Long id) {
        return new ResourceNotFoundException(what + " " + id + " was not found");
    }
}
