package com.collabo.backend.exception;

/**
 * Thrown when an IP exceeds the registration quota and must prove it is human
 * by answering a math challenge before it can continue.
 */
public class RateLimitChallengeException extends RuntimeException {

    private final String challenge;
    private final String challengeToken;

    public RateLimitChallengeException(String challenge, String challengeToken) {
        super("Too many registrations from the same network — prove you're human");
        this.challenge = challenge;
        this.challengeToken = challengeToken;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getChallengeToken() {
        return challengeToken;
    }
}
