package com.ruoyi.aden.domain.task;

import java.util.Objects;
import java.util.UUID;

public record AdenTaskStepId(String value) {
    public AdenTaskStepId {
        Objects.requireNonNull(value, "stepId");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("stepId 必须是规范小写 UUID");
    }
}
