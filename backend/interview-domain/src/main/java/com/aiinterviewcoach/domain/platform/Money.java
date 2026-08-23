package com.aiinterviewcoach.domain.platform;

import java.math.BigDecimal;
import java.util.Locale;

/** 精确金额；禁止 binary floating point。 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        amount = DomainPreconditions.requireNonNull(amount, "amount");
        DomainPreconditions.require(amount.scale() <= 8, DomainErrorCode.INVALID_ARGUMENT,
                "amount scale exceeds supported precision");
        currency = DomainPreconditions.requireText(currency, "currency").toUpperCase(Locale.ROOT);
        DomainPreconditions.require(currency.matches("[A-Z]{3}"), DomainErrorCode.INVALID_ARGUMENT,
                "currency must be an ISO-4217 alpha code");
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        DomainPreconditions.requireNonNull(other, "otherMoney");
        DomainPreconditions.require(currency.equals(other.currency), DomainErrorCode.INVALID_ARGUMENT,
                "money currencies must match");
    }
}
