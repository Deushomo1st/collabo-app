package com.collabo.backend.exception;

/** Thrown when registering/creating a user whose email is already taken. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("This email is already registered: " + email);
    }
}
