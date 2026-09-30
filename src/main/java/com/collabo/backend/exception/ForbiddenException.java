package com.collabo.backend.exception;

/** The person may see this but lacks the permission to change it. Answered with 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
