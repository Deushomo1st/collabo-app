package com.collabo.backend.exception;

/**
 * Thrown when a verification code has passed its expiry window.
 */
public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException() {
        super("Verification code has expired. Request a new one.");
    }
}
