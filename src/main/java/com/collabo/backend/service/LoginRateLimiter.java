package com.collabo.backend.service;

import com.collabo.backend.exception.RateLimitedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits failed logins: N failures per key (client IP + identifier) inside a window, then a lockout
 * until the window ends. A successful login clears the key.
 */
@Service
public class LoginRateLimiter {

    private static final int PURGE_ABOVE = 10_000;

    private record Attempts(int failures, long windowStart) {}

    private final int maxFailures;
    private final long windowMillis;
    // ponytail: in-memory, per server. Move to the database if the app ever runs on several servers.
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.login-limit.max-failures:5}") int maxFailures,
                            @Value("${app.login-limit.window-minutes:15}") long windowMinutes) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowMinutes * 60_000L;
    }

    /** Throws RateLimitedException while the key is locked out. */
    public void check(String key) {
        Attempts a = attempts.get(key);
        long now = System.currentTimeMillis();
        if (a != null && now - a.windowStart() < windowMillis && a.failures() >= maxFailures) {
            throw new RateLimitedException((a.windowStart() + windowMillis - now + 999) / 1000);
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        if (attempts.size() > PURGE_ABOVE) attempts.values().removeIf(a -> now - a.windowStart() >= windowMillis);
        attempts.merge(key, new Attempts(1, now),
                (old, fresh) -> now - old.windowStart() >= windowMillis ? fresh : new Attempts(old.failures() + 1, old.windowStart()));
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
