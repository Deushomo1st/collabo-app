package com.collabo.backend.exception;

/**
 * Thrown when an IP is locked out of registration and must wait before retrying.
 * Carries the remaining wait so the frontend can render a live countdown.
 */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super("Too many attempts. Please slow down.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
