package com.ruoyi.aden.application.idempotency;

import java.util.Objects;
import java.util.regex.Pattern;

public record AdenIdempotencyKey(String value) {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public AdenIdempotencyKey {
        Objects.requireNonNull(value, "idempotencyKey");
        if (!SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException("Idempotency-Key 必须为 1..128 个安全 ASCII 字符");
        }
    }
}
