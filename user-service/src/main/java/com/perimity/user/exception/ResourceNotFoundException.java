package com.perimity.user.exception;

/**
 * Thrown when a row that the caller named does not exist, or exists on a
 * different campus.
 *
 * Deliberately NOT an IllegalArgumentException: the handler maps that to 400,
 * and "you asked for department 99 and there is no department 99" is a 404.
 * Getting this wrong makes the frontend unable to tell a bad request from a
 * missing row.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Long id) {
        return new ResourceNotFoundException(what + " " + id + " was not found");
    }
}
