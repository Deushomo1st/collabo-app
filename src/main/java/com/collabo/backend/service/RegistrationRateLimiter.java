package com.collabo.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-IP rate limiter for registration. Quota = N attempts per rolling window.
 * Once the quota is exceeded the caller is asked to prove they are human with a
 * simple math challenge (server-minted, so a bot cannot read the answer). A
 * correct answer resets the budget; a wrong answer escalates a lockout ladder
 * that caps at 1 hour.
 */
@Service
public class RegistrationRateLimiter {

    /** Escalating lockout ladder, in seconds (caps at 60 minutes). */
    private static final long[] LADDER_SECONDS =
            {5, 10, 20, 30, 60, 120, 300, 600, 1200, 1800, 3600};

    /** An IP that goes quiet this long is reset to a clean slate. */
    private static final long RESET_IDLE_MILLIS = 60 * 60 * 1000L;

    private final int maxAttempts;
    private final long windowMillis;
    private final boolean challengeEnabled;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public RegistrationRateLimiter(
            @Value("${app.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.rate-limit.window-minutes:1}") long windowMinutes,
            @Value("${app.rate-limit.challenge-enabled:true}") boolean challengeEnabled) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMinutes * 60 * 1000L;
        this.challengeEnabled = challengeEnabled;
    }

    /**
     * Decide whether the request may proceed. Returns one of:
     *   allow()            -> proceed
     *   challenge(c)       -> 429 + math challenge (must be answered)
     *   locked(seconds)    -> 429 + wait this long (escalating)
     */
    public RateLimitResult check(String ip, String challengeToken, String challengeAnswer) {
        long now = System.currentTimeMillis();
        Entry e = entries.computeIfAbsent(ip, k -> new Entry());

        // Long idle period -> reset to a clean slate.
        if (now - e.lastSeen > RESET_IDLE_MILLIS) {
            e.windowStart = now;
            e.attempts = 0;
            e.level = 0;
            e.lockedUntil = 0;
            e.challenge = null;
        }
        e.lastSeen = now;

        // 1. Currently locked out -> report remaining wait.
        if (e.lockedUntil > now) {
            return RateLimitResult.locked(ceilSeconds(e.lockedUntil - now));
        }

        // 2. A challenge answer was supplied -> verify it against the pending one.
        if (challengeEnabled && challengeToken != null && challengeAnswer != null) {
            Integer answer = parseAnswer(challengeAnswer);
            if (e.challenge != null
                    && e.challenge.token().equals(challengeToken)
                    && answer != null
                    && answer == e.challenge.answer()) {
                // Human verified -> reset budget and allow.
                e.attempts = 0;
                e.level = 0;
                e.challenge = null;
                return RateLimitResult.allow();
            }
            // Wrong / stale answer -> escalate the lockout.
            long lockSecs = ladderAt(e.level);
            e.lockedUntil = now + lockSecs * 1000L;
            e.level = Math.min(e.level + 1, LADDER_SECONDS.length - 1);
            e.challenge = null;
            return RateLimitResult.locked(lockSecs);
        }

        // 3. Rolling window expired -> start a fresh window.
        if (now - e.windowStart >= windowMillis) {
            e.windowStart = now;
            e.attempts = 0;
        }

        // 4. Within quota -> allow (count this attempt).
        if (e.attempts < maxAttempts) {
            e.attempts++;
            return RateLimitResult.allow();
        }

        // 5. Over quota -> challenge (or plain lockout if challenges are off).
        if (challengeEnabled) {
            e.challenge = mintChallenge();
            return RateLimitResult.challenge(e.challenge);
        }
        long lockSecs = ladderAt(e.level);
        e.lockedUntil = now + lockSecs * 1000L;
        e.level = Math.min(e.level + 1, LADDER_SECONDS.length - 1);
        return RateLimitResult.locked(lockSecs);
    }

    private Challenge mintChallenge() {
        int a = ThreadLocalRandom.current().nextInt(1, 10);
        int b = ThreadLocalRandom.current().nextInt(1, 10);
        return new Challenge("What is " + a + " + " + b + "?", UUID.randomUUID().toString(), a + b);
    }

    private static long ladderAt(int level) {
        return LADDER_SECONDS[Math.min(level, LADDER_SECONDS.length - 1)];
    }

    private static Integer parseAnswer(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long ceilSeconds(long millis) {
        return (millis + 999) / 1000;
    }

    // ---- result / challenge types ----

    public record RateLimitResult(boolean allowed, Challenge challenge, long retryAfterSeconds) {
        static RateLimitResult allow() {
            return new RateLimitResult(true, null, 0);
        }
        static RateLimitResult challenge(Challenge c) {
            return new RateLimitResult(false, c, 0);
        }
        static RateLimitResult locked(long seconds) {
            return new RateLimitResult(false, null, seconds);
        }
    }

    public record Challenge(String question, String token, int answer) {}

    private static class Entry {
        long windowStart;
        int attempts;
        int level;
        long lockedUntil;
        long lastSeen;
        Challenge challenge;
    }
}
