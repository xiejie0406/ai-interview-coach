package com.aiinterviewcoach.domain.platform;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 纯 Java 的领域输入与状态校验工具，集中避免用基础设施异常表达业务规则。
 */
public final class DomainPreconditions {

    private DomainPreconditions() {
    }

    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, name + " must not be null");
        }
        return value;
    }

    public static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, name + " must not be blank");
        }
        return value.trim();
    }

    public static <T extends Collection<?>> T requireNonEmpty(T value, String name) {
        requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, name + " must not be empty");
        }
        return value;
    }

    public static <K, V> Map<K, V> requireNonEmpty(Map<K, V> value, String name) {
        requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, name + " must not be empty");
        }
        return value;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String name) {
        requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT, name + " must not be negative");
        }
        return value;
    }

    public static void require(boolean condition, DomainErrorCode code, String message) {
        if (!condition) {
            throw new DomainException(Objects.requireNonNull(code, "code must not be null"), message);
        }
    }
}
