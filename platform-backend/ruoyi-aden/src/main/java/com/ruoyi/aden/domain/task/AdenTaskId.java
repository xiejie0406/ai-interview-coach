package com.ruoyi.aden.domain.task;

import java.util.Objects;
import java.util.UUID;

public record AdenTaskId(String value) {
    public AdenTaskId {
        Objects.requireNonNull(value, "taskId");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("taskId 必须是规范小写 UUID");
    }
}
