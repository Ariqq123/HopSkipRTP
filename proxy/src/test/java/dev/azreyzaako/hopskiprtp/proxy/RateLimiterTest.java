package dev.azreyzaako.hopskiprtp.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void enforcesGlobalAndPerPlayerBursts() {
        RateLimiter limiter = new RateLimiter(2, 1, 1, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(second));
        assertFalse(limiter.tryAcquire(UUID.randomUUID()));
    }

    @Test
    void cleanupRemovesInactivePlayerBuckets() throws Exception {
        RateLimiter limiter = new RateLimiter(10, 10, 3, 3);
        UUID playerId = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(playerId));

        Field bucketsField = RateLimiter.class.getDeclaredField("playerBuckets");
        bucketsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<UUID, Object> buckets =
            (java.util.concurrent.ConcurrentHashMap<UUID, Object>) bucketsField.get(limiter);
        Object bucket = buckets.get(playerId);

        Field lastRefillField = bucket.getClass().getDeclaredField("lastRefillNanos");
        lastRefillField.setAccessible(true);
        lastRefillField.setLong(bucket, System.nanoTime() - 120_000_000_000L);

        limiter.cleanup();
        assertFalse(buckets.containsKey(playerId));
    }
}
