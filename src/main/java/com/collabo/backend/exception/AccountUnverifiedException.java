package com.collabo.backend.exception;

/**
 * Thrown when someone tries to register an email that already has an account
 * which is NOT yet verified. The account exists but the user must confirm it
 * via the OTP sent to that email (they can request a resend).
 */
public class AccountUnverifiedException extends RuntimeException {

    public AccountUnverifiedException() {
        super("This email is registered but not yet verified. Check your inbox for your verification code.");
    }
}
