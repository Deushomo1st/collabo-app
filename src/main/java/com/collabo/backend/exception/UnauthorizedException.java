package com.collabo.backend.exception;

/** The call needs a signed-in user and there is none. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Sign in first.");
    }
}
