package com.collabo.backend.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A token bucket per key. Every request takes one token; tokens flow back at a steady rate up to the capacity. So capacity is the burst
 * a caller may make at once, and the refill rate is the long-run rate. There is no timer: a bucket is brought up to date from the time
 * elapsed since it was last touched. compute() makes the read-update-write atomic per key, so two simultaneous requests cannot both take the last token.
 *
 * ponytail: in-memory, per server (the app runs as one container). Move the map to a shared store if it ever runs on several servers.
 */
public class TokenBucketLimiter {

    private static final int PURGE_ABOVE = 10_000;

    private record Bucket(double tokens, long lastNanos) {}

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanos;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(double capacity, double refillPerSecond) { this(capacity, refillPerSecond, System::nanoTime); }

    /** The clock is injectable so tests can move time. */
    public TokenBucketLimiter(double capacity, double refillPerSecond, LongSupplier nanos) {
        if (capacity < 1 || refillPerSecond <= 0) throw new IllegalArgumentException("capacity must be at least 1 and the refill rate positive");
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanos = nanos;
    }

    /** 0 when the request may go ahead (a token was taken); otherwise the whole seconds until one token is back. */
    public long tryAcquire(String key) {
        long now = nanos.getAsLong();
        if (buckets.size() > PURGE_ABOVE) purge(now);
        long[] wait = {0};
        buckets.compute(key, (k, b) -> {
            double tokens = b == null ? capacity : refilled(b, now);
            if (tokens >= 1) return new Bucket(tokens - 1, now);
            wait[0] = Math.max(1, (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000_000d));
            return new Bucket(tokens, now);
        });
        return wait[0];
    }

    private double refilled(Bucket b, long now) { return Math.min(capacity, b.tokens() + (now - b.lastNanos()) * refillPerNano); }

    /** A bucket that has refilled completely is the same as no bucket, so it can go. */
    private void purge(long now) { buckets.values().removeIf(b -> refilled(b, now) >= capacity); }
}
