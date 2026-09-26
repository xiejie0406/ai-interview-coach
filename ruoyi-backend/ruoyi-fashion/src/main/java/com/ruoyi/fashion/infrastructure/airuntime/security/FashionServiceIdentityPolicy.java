package com.ruoyi.fashion.infrastructure.airuntime.security;

import java.time.Duration;
import java.util.Objects;

/** 本地/对端身份以及签名时间窗。 */
public record FashionServiceIdentityPolicy(
        String localServiceId,
        String peerServiceId,
        Duration maxClockSkew,
        Duration nonceTtl) {

    private static final Duration MINIMUM_NONCE_TTL = Duration.ofMinutes(10);

    public FashionServiceIdentityPolicy {
        localServiceId = serviceToken(localServiceId, "localServiceId");
        peerServiceId = serviceToken(peerServiceId, "peerServiceId");
        Objects.requireNonNull(maxClockSkew, "maxClockSkew");
        Objects.requireNonNull(nonceTtl, "nonceTtl");
        if (localServiceId.equals(peerServiceId)) {
            throw new IllegalArgumentException("localServiceId 与 peerServiceId 不能相同");
        }
        if (maxClockSkew.isZero() || maxClockSkew.isNegative()) {
            throw new IllegalArgumentException("maxClockSkew 必须大于 0");
        }
        Duration requiredTtl = maxClockSkew.multipliedBy(2);
        if (requiredTtl.compareTo(MINIMUM_NONCE_TTL) < 0) {
            requiredTtl = MINIMUM_NONCE_TTL;
        }
        if (nonceTtl.compareTo(requiredTtl) < 0) {
            throw new IllegalArgumentException("nonceTtl 必须至少为 10 分钟且不小于两倍时钟偏差");
        }
    }

    public static FashionServiceIdentityPolicy javaControlPlaneDefaults() {
        return new FashionServiceIdentityPolicy(
                "ruoyi-fashion",
                "fashion-ai-runtime",
                Duration.ofMinutes(5),
                Duration.ofMinutes(10));
    }

    static String serviceToken(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            throw new IllegalArgumentException(fieldName + " 必须是安全的服务标识");
        }
        return value;
    }
}
