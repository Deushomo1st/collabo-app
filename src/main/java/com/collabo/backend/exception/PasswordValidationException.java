package com.collabo.backend.exception;

/**
 * Thrown when a registration password fails the strength rules:
 * too short (below the hard floor) or not "secure" and not explicitly
 * acknowledged via the weak-password "proceed anyway" flow.
 */
public class PasswordValidationException extends RuntimeException {

    public PasswordValidationException(String message) {
        super(message);
    }
}
