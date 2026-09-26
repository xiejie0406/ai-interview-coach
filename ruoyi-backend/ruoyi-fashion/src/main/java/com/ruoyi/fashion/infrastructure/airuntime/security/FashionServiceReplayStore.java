package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.time.Instant;

/**
 * 原子占用 nonce 的边界。当前实现仅适用于单进程；多副本部署前必须替换为共享、具 TTL 的实现。
 */
public interface FashionServiceReplayStore {

    ReserveResult reserve(String serviceId, String nonce, Instant expiresAt, Instant now);

    enum ReserveResult {
        CLAIMED,
        REPLAYED,
        CAPACITY_EXCEEDED
    }
}
