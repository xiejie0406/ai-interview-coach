package com.ruoyi.fashion.application.settings;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Predicate;

/** 单个白名单配置定义；Secret 与人员范围不得注册在这里。 */
public record FashionSettingDefinition(
        String key,
        String name,
        String description,
        FashionSettingType type,
        String defaultValue,
        String dictionaryType,
        Predicate<String> validator,
        String validationMessage) {

    public FashionSettingDefinition {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(type);
        Objects.requireNonNull(defaultValue);
        Objects.requireNonNull(validator);
        Objects.requireNonNull(validationMessage);
    }

    public String validate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!validator.test(normalized)) {
            throw new IllegalArgumentException(validationMessage);
        }
        return normalized;
    }

    static Predicate<String> integerRange(int minimum, int maximum) {
        return value -> {
            try {
                int parsed = Integer.parseInt(value);
                return parsed >= minimum && parsed <= maximum;
            } catch (NumberFormatException exception) {
                return false;
            }
        };
    }

    static Predicate<String> decimalRange(String minimum, String maximum) {
        BigDecimal min = new BigDecimal(minimum);
        BigDecimal max = new BigDecimal(maximum);
        return value -> {
            if (!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?")) {
                return false;
            }
            try {
                BigDecimal parsed = new BigDecimal(value);
                return parsed.scale() <= 2 && parsed.compareTo(min) >= 0 && parsed.compareTo(max) <= 0;
            } catch (NumberFormatException exception) {
                return false;
            }
        };
    }
}
