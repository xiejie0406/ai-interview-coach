package com.ruoyi.aden.domain.runner;

import java.time.Instant;
import java.util.Objects;

public record AdenLease(
        AdenSessionId ownerSessionId,
        long ownerSessionEpoch,
        AdenFencingToken fencingToken,
        Instant leaseUntil) {
    public AdenLease {
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        if (ownerSessionEpoch < 1) throw new IllegalArgumentException("ownerSessionEpoch 必须为正数");
        Objects.requireNonNull(fencingToken, "fencingToken");
        if (fencingToken.value() < 1) throw new IllegalArgumentException("已领取 lease 的 fence 必须为正数");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
    }

    public boolean validFor(AdenSessionId sessionId, long sessionEpoch,
                            AdenFencingToken token, Instant now) {
        return ownerSessionId.equals(sessionId) && ownerSessionEpoch == sessionEpoch
                && fencingToken.equals(token) && now.isBefore(leaseUntil);
    }
}
