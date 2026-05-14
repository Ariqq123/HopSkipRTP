package dev.azreyzaako.hopskiprtp.proxy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token-bucket rate limiter for RTP requests.
 * Limits both per-player and global request rates.
 */
final class RateLimiter {

    private final int globalBurst;
    private final int globalRefillPerSecond;
    private final int playerBurst;
    private final int playerRefillPerSecond;

    private final AtomicInteger globalTokens;
    private volatile long globalLastRefillNanos;
    private final ConcurrentHashMap<UUID, PlayerBucket> playerBuckets = new ConcurrentHashMap<>();

    RateLimiter(int globalBurst, int globalRefillPerSecond, int playerBurst, int playerRefillPerSecond) {
        this.globalBurst = Math.max(1, globalBurst);
        this.globalRefillPerSecond = Math.max(1, globalRefillPerSecond);
        this.playerBurst = Math.max(1, playerBurst);
        this.playerRefillPerSecond = Math.max(1, playerRefillPerSecond);
        this.globalTokens = new AtomicInteger(this.globalBurst);
        this.globalLastRefillNanos = System.nanoTime();
    }

    /**
     * Returns true if the request is allowed, false if rate-limited.
     */
    boolean tryAcquire(UUID playerId) {
        // Check global limit first
        refillGlobal();
        if (globalTokens.decrementAndGet() < 0) {
            globalTokens.incrementAndGet(); // give it back
            return false;
        }

        // Check per-player limit
        PlayerBucket bucket = playerBuckets.computeIfAbsent(playerId, id -> new PlayerBucket(playerBurst, playerRefillPerSecond));
        if (!bucket.tryAcquire()) {
            globalTokens.incrementAndGet(); // give global token back
            return false;
        }

        return true;
    }

    void cleanup() {
        long now = System.nanoTime();
        playerBuckets.entrySet().removeIf(entry -> {
            PlayerBucket bucket = entry.getValue();
            long elapsedSeconds = (now - bucket.lastRefillNanos) / 1_000_000_000L;
            return elapsedSeconds > 60 && bucket.tokens.get() >= bucket.burst;
        });
    }

    private void refillGlobal() {
        long now = System.nanoTime();
        long elapsedNanos = now - globalLastRefillNanos;
        long tokensToAdd = (elapsedNanos * globalRefillPerSecond) / 1_000_000_000L;
        if (tokensToAdd > 0) {
            globalLastRefillNanos = now;
            globalTokens.updateAndGet(current -> Math.min(globalBurst, current + (int) tokensToAdd));
        }
    }

    private static final class PlayerBucket {
        private final int burst;
        private final int refillPerSecond;
        private final AtomicInteger tokens;
        private volatile long lastRefillNanos;

        PlayerBucket(int burst, int refillPerSecond) {
            this.burst = burst;
            this.refillPerSecond = refillPerSecond;
            this.tokens = new AtomicInteger(burst);
            this.lastRefillNanos = System.nanoTime();
        }

        boolean tryAcquire() {
            refill();
            return tokens.decrementAndGet() >= 0;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;
            long tokensToAdd = (elapsedNanos * refillPerSecond) / 1_000_000_000L;
            if (tokensToAdd > 0) {
                lastRefillNanos = now;
                tokens.updateAndGet(current -> Math.min(burst, current + (int) tokensToAdd));
            }
        }
    }
}
