package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** IMP-01 单进程可测实现；不冒充多副本共享 replay store。 */
public final class InMemoryFashionServiceReplayStore implements FashionServiceReplayStore {

    private static final int DEFAULT_MAX_ENTRIES = 100_000;

    private final Map<String, Instant> expirations = new HashMap<>();
    private final PriorityQueue<Expiry> expiryQueue = new PriorityQueue<>();
    private final int maxEntries;

    public InMemoryFashionServiceReplayStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryFashionServiceReplayStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries 必须大于 0");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized ReserveResult reserve(
            String serviceId,
            String nonce,
            Instant expiresAt,
            Instant now) {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 now");
        }
        evictExpired(now);
        String replayKey = serviceId + '\n' + nonce;
        Instant currentExpiry = expirations.get(replayKey);
        if (currentExpiry != null && currentExpiry.isAfter(now)) {
            return ReserveResult.REPLAYED;
        }
        if (expirations.size() >= maxEntries && !expirations.containsKey(replayKey)) {
            return ReserveResult.CAPACITY_EXCEEDED;
        }
        expirations.put(replayKey, expiresAt);
        expiryQueue.add(new Expiry(replayKey, expiresAt));
        return ReserveResult.CLAIMED;
    }

    private void evictExpired(Instant now) {
        while (!expiryQueue.isEmpty() && !expiryQueue.peek().expiresAt().isAfter(now)) {
            Expiry expired = expiryQueue.remove();
            expirations.remove(expired.replayKey(), expired.expiresAt());
        }
    }

    private record Expiry(String replayKey, Instant expiresAt) implements Comparable<Expiry> {
        @Override
        public int compareTo(Expiry other) {
            int byExpiry = expiresAt.compareTo(other.expiresAt);
            return byExpiry != 0 ? byExpiry : replayKey.compareTo(other.replayKey);
        }
    }
}
