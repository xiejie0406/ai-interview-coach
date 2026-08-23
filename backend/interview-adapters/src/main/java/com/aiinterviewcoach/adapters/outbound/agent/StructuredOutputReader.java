package com.aiinterviewcoach.adapters.outbound.agent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JSON-compatible Map 的严格读取器；拒绝未知字段、宽松数值转换和隐式 String coercion。 */
final class StructuredOutputReader {

    private final Map<String, Object> value;

    private StructuredOutputReader(Map<String, Object> value, Set<String> required, Set<String> optional) {
        this.value = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
        Set<String> allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!allowed.containsAll(value.keySet()) || !value.keySet().containsAll(required)) {
            throw new IllegalArgumentException("structured output fields do not match schema");
        }
    }

    static StructuredOutputReader root(
            Map<String, Object> value,
            Set<String> required,
            Set<String> optional
    ) {
        return new StructuredOutputReader(java.util.Objects.requireNonNull(value), required, optional);
    }

    static StructuredOutputReader object(
            Object value,
            Set<String> required,
            Set<String> optional
    ) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("structured output value must be an object");
        }
        LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
        source.forEach((key, nested) -> {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException("structured output keys must be strings");
            }
            typed.put(text, nested);
        });
        return new StructuredOutputReader(typed, required, optional);
    }

    String text(String key, int minLength, int maxLength) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.length() < minLength || text.length() > maxLength) {
            throw new IllegalArgumentException("structured output text field is invalid: " + key);
        }
        return text;
    }

    Optional<String> nullableText(String key, int minLength, int maxLength) {
        Object raw = value.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(text(key, minLength, maxLength));
    }

    boolean bool(String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Boolean result)) {
            throw new IllegalArgumentException("structured output boolean field is invalid: " + key);
        }
        return result;
    }

    int integer(String key, int minimum, int maximum) {
        Object raw = value.get(key);
        final long parsed;
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            parsed = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger integer) {
            try {
                parsed = integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("structured output integer is out of range: " + key, exception);
            }
        } else if (raw instanceof BigDecimal decimal) {
            try {
                parsed = decimal.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("structured output integer is not exact: " + key, exception);
            }
        } else {
            throw new IllegalArgumentException("structured output integer field is invalid: " + key);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("structured output integer is outside schema range: " + key);
        }
        return (int) parsed;
    }

    Optional<Integer> nullableInteger(String key, int minimum, int maximum) {
        return value.get(key) == null ? Optional.empty() : Optional.of(integer(key, minimum, maximum));
    }

    List<StructuredOutputReader> objects(
            String key,
            int minimumItems,
            int maximumItems,
            Set<String> required,
            Set<String> optional
    ) {
        List<?> values = list(key, minimumItems, maximumItems);
        List<StructuredOutputReader> result = new ArrayList<>(values.size());
        values.forEach(item -> result.add(object(item, required, optional)));
        return List.copyOf(result);
    }

    List<String> strings(
            String key,
            int minimumItems,
            int maximumItems,
            int maximumLength,
            boolean unique
    ) {
        List<?> values = list(key, minimumItems, maximumItems);
        List<String> result = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank() || text.length() > maximumLength) {
                throw new IllegalArgumentException("structured output string array is invalid: " + key);
            }
            result.add(text);
        }
        if (unique && new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("structured output string array is not unique: " + key);
        }
        return List.copyOf(result);
    }

    Optional<Instant> nullableInstant(String key) {
        Optional<String> value = nullableText(key, 1, 64);
        try {
            return value.map(Instant::parse);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("structured output timestamp is invalid: " + key, exception);
        }
    }

    private List<?> list(String key, int minimumItems, int maximumItems) {
        Object raw = value.get(key);
        if (!(raw instanceof List<?> list) || list.size() < minimumItems || list.size() > maximumItems) {
            throw new IllegalArgumentException("structured output array field is invalid: " + key);
        }
        return list;
    }
}
