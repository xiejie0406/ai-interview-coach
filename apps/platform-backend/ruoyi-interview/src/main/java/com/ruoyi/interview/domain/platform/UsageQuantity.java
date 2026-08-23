package com.ruoyi.interview.domain.platform;

import java.math.BigDecimal;

/**
 * 版本化规则解释的非负用量。unit 是受批准的业务单位，不由 Provider 或客户端任意指定。
 */
public record UsageQuantity(String unit, BigDecimal value) {

    public UsageQuantity {
        unit = DomainPreconditions.requireText(unit, "usageUnit");
        value = DomainPreconditions.requireNonNegative(value, "usageValue");
        DomainPreconditions.require(value.scale() <= 8, DomainErrorCode.INVALID_ARGUMENT,
                "usage value scale exceeds supported precision");
    }

    public static UsageQuantity zero(String unit) {
        return new UsageQuantity(unit, BigDecimal.ZERO);
    }

    public UsageQuantity plus(UsageQuantity other) {
        requireSameUnit(other);
        return new UsageQuantity(unit, value.add(other.value));
    }

    public UsageQuantity minus(UsageQuantity other) {
        requireSameUnit(other);
        DomainPreconditions.require(value.compareTo(other.value) >= 0, DomainErrorCode.INVALID_ARGUMENT,
                "usage quantity cannot become negative");
        return new UsageQuantity(unit, value.subtract(other.value));
    }

    public boolean isGreaterThan(UsageQuantity other) {
        requireSameUnit(other);
        return value.compareTo(other.value) > 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    private void requireSameUnit(UsageQuantity other) {
        DomainPreconditions.requireNonNull(other, "otherUsageQuantity");
        DomainPreconditions.require(unit.equals(other.unit), DomainErrorCode.INVALID_ARGUMENT,
                "usage units must match");
    }
}
