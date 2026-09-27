package com.collabo.backend.exception;

/** Thrown when a requested resource (e.g. a user id) does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
