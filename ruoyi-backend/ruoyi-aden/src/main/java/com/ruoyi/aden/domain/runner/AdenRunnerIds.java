package com.ruoyi.aden.domain.runner;

import java.util.Objects;
import java.util.UUID;

final class AdenRunnerIds {
    private AdenRunnerIds() { }

    static String requireUuid(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException(name + " 必须是规范小写 UUID");
        }
        return value;
    }
}
