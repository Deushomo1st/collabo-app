package com.collabo.backend.exception;

/**
 * Thrown when a registration email fails the format check. Only enforced when
 * test mode is OFF (test mode skips the "is this a real email" check).
 */
public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException() {
        super("Not a valid email address");
    }
}
