package com.perimity.gatepass.storage;

/** Storage failed. Mapped to 500 - the caller did nothing wrong. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
