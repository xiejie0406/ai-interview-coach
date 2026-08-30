package com.ruoyi.interview.domain.platform;

/** 乐观锁版本；版本只允许单调递增。 */
public record AggregateVersion(long value) {

    public AggregateVersion {
        DomainPreconditions.require(value >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "aggregate version must not be negative");
    }

    public static AggregateVersion initial() {
        return new AggregateVersion(0);
    }

    public AggregateVersion next() {
        if (value == Long.MAX_VALUE) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "aggregate version overflow");
        }
        return new AggregateVersion(value + 1);
    }

    public void requireMatches(AggregateVersion expected) {
        DomainPreconditions.requireNonNull(expected, "expectedVersion");
        DomainPreconditions.require(value == expected.value, DomainErrorCode.VERSION_CONFLICT,
                "aggregate version does not match expected version");
    }
}
