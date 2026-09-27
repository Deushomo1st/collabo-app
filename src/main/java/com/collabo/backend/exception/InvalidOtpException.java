package com.collabo.backend.exception;

/**
 * Thrown when a submitted verification code does not match the one on file.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException() {
        super("Invalid verification code");
    }
}
