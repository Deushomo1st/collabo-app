package com.collabo.backend;

import com.collabo.backend.config.TokenBucketLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The bucket itself, on a fake clock: burst, refill, cap, per-key isolation, and no double-spend under contention. */
class TokenBucketLimiterTest {

    static final long SECOND = 1_000_000_000L;
    final AtomicLong clock = new AtomicLong();

    // burst 3, one token back every 2 seconds
    TokenBucketLimiter bucket() { return new TokenBucketLimiter(3, 0.5, clock::get); }

    @Test
    void aFullBucketAllowsTheBurstThenRefusesWithATimeToWait() {
        var b = bucket();
        for (int i = 0; i < 3; i++) assertEquals(0, b.tryAcquire("a"));
        assertEquals(2, b.tryAcquire("a"));   // empty: one token is two seconds away
    }

    @Test
    void tokensFlowBackContinuouslyNotAtAWindowEdge() {
        var b = bucket();
        for (int i = 0; i < 3; i++) b.tryAcquire("a");
        clock.addAndGet(SECOND);               // half a token: still no
        assertEquals(1, b.tryAcquire("a"));
        clock.addAndGet(SECOND);               // a whole token has come back
        assertEquals(0, b.tryAcquire("a"));
        assertEquals(2, b.tryAcquire("a"));    // and only one
    }

    @Test
    void neverFillsPastTheCapacity() {
        var b = bucket();
        b.tryAcquire("a");
        clock.addAndGet(3600 * SECOND);        // an hour idle
        for (int i = 0; i < 3; i++) assertEquals(0, b.tryAcquire("a"));
        assertTrue(b.tryAcquire("a") > 0);     // still only the capacity of 3
    }

    @Test
    void keysDoNotShareABucket() {
        var b = bucket();
        for (int i = 0; i < 3; i++) b.tryAcquire("a");
        assertTrue(b.tryAcquire("a") > 0);
        assertEquals(0, b.tryAcquire("b"));
    }

    @Test
    void simultaneousCallersCannotSpendTheSameToken() throws Exception {
        var b = new TokenBucketLimiter(50, 0.001, clock::get);   // effectively no refill during the test
        AtomicLong allowed = new AtomicLong();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> { for (int i = 0; i < 100; i++) if (b.tryAcquire("x") == 0) allowed.incrementAndGet(); });
            threads[t].start();
        }
        for (Thread t : threads) t.join();
        assertEquals(50, allowed.get());
    }

    @Test
    void rejectsNonsenseSettings() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(5, 0));
    }
}
