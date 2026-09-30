package com.collabo.backend.exception;

/** Login failed. One message for "no such account" and "wrong password", so accounts can't be probed. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Wrong email, username or password.");
    }
}
