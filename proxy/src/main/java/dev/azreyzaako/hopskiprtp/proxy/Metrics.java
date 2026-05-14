package dev.azreyzaako.hopskiprtp.proxy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory metrics counters for RTP operations.
 * Can be queried via /rtp status if extended, or logged periodically.
 */
final class Metrics {

    private final AtomicLong requestsTotal = new AtomicLong(0);
    private final AtomicLong successesTotal = new AtomicLong(0);
    private final AtomicLong failuresTotal = new AtomicLong(0);
    private final AtomicLong rateLimitedTotal = new AtomicLong(0);
    private final AtomicLong cooldownBlockedTotal = new AtomicLong(0);
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    void incrementRequests() {
        requestsTotal.incrementAndGet();
        pendingCount.incrementAndGet();
    }

    void incrementSuccess() {
        successesTotal.incrementAndGet();
        pendingCount.decrementAndGet();
    }

    void incrementFailure() {
        failuresTotal.incrementAndGet();
        pendingCount.decrementAndGet();
    }

    void incrementRateLimited() {
        rateLimitedTotal.incrementAndGet();
    }

    void incrementCooldownBlocked() {
        cooldownBlockedTotal.incrementAndGet();
    }

    Snapshot snapshot() {
        return new Snapshot(
            requestsTotal.get(),
            successesTotal.get(),
            failuresTotal.get(),
            rateLimitedTotal.get(),
            cooldownBlockedTotal.get(),
            pendingCount.get()
        );
    }

    record Snapshot(
        long requestsTotal,
        long successesTotal,
        long failuresTotal,
        long rateLimitedTotal,
        long cooldownBlockedTotal,
        int pendingCount
    ) {
    }
}
