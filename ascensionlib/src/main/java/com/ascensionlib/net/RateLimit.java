package com.ascensionlib.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A small token bucket per (player, channel), for the requests a client can send as often as it likes (opening a screen, asking for an
 * inspection). Every one of them costs the server thread a store read and an ownership scan, so an unthrottled client could use them
 * to lag the whole server. Each bucket holds {@code burst} tokens and refills {@code perSecond} of them a second; a request that finds
 * the bucket empty is refused and should simply be dropped (or answered with a short "slow down").
 *
 * <p>Server thread only (the networking receivers run there); synchronized anyway because it is cheap and a mistake here would be a data race.
 * {@link #forget} must be called when a player leaves so the table cannot grow.
 */
public final class RateLimit {
    private static final Map<UUID, Map<String, Bucket>> BUCKETS = new HashMap<>();

    private static final class Bucket {
        double tokens;
        long updatedNanos;
        Bucket(double tokens, long now) { this.tokens = tokens; this.updatedNanos = now; }
    }

    private RateLimit() {}

    /** Whether this player may make one more {@code channel} request now. Consumes a token when it returns true. */
    public static synchronized boolean allow(UUID player, String channel, int burst, double perSecond) {
        return allow(player, channel, burst, perSecond, System.nanoTime());
    }

    /** As {@link #allow(UUID, String, int, double)} with the clock supplied, so a test controls time. */
    static synchronized boolean allow(UUID player, String channel, int burst, double perSecond, long nowNanos) {
        if (burst < 1 || perSecond <= 0) throw new IllegalArgumentException("burst and rate must be positive");
        var bucket = BUCKETS.computeIfAbsent(player, id -> new HashMap<>()).computeIfAbsent(channel, id -> new Bucket(burst, nowNanos));
        double elapsedSeconds = Math.max(0, nowNanos - bucket.updatedNanos) / 1_000_000_000.0;
        bucket.tokens = Math.min(burst, bucket.tokens + elapsedSeconds * perSecond);
        bucket.updatedNanos = nowNanos;
        if (bucket.tokens < 1.0) return false;
        bucket.tokens -= 1.0;
        return true;
    }

    /** Drops everything remembered about a player (they left). */
    public static synchronized void forget(UUID player) { BUCKETS.remove(player); }

    /** Drops everything (the server stopped). */
    public static synchronized void clear() { BUCKETS.clear(); }

    static synchronized int tracked() { return BUCKETS.size(); }
}
