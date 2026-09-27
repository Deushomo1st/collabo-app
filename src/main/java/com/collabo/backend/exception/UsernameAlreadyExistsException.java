package com.collabo.backend.exception;

/** Thrown when creating a user whose username is already taken. */
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("This username is already taken: " + username);
    }
}
