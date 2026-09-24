package com.ruoyi.aden.domain.task;

import java.util.Objects;
import java.util.UUID;

public record AdenCorrelationId(String value) {
    public AdenCorrelationId {
        Objects.requireNonNull(value, "correlationId");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("correlationId 必须是规范小写 UUID");
        }
    }
}
