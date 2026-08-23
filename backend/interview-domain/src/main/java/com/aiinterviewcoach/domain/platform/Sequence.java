package com.aiinterviewcoach.domain.platform;

/** 单一聚合或流内严格递增的序列号，不是全局时钟。 */
public record Sequence(long value) {

    public Sequence {
        DomainPreconditions.require(value >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "sequence must not be negative");
    }

    public static Sequence initial() {
        return new Sequence(0);
    }

    public Sequence next() {
        if (value == Long.MAX_VALUE) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "sequence overflow");
        }
        return new Sequence(value + 1);
    }
}
