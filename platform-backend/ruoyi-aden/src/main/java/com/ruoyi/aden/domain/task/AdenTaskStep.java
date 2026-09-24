package com.ruoyi.aden.domain.task;

import java.util.Objects;

public record AdenTaskStep(
        AdenTaskStepId id,
        int ordinal,
        int attemptNo,
        AdenTaskStepState state,
        long version) {

    public AdenTaskStep {
        Objects.requireNonNull(id, "id");
        if (ordinal < 1 || ordinal > 64) throw new IllegalArgumentException("ordinal 必须在 1..64");
        if (attemptNo < 0 || attemptNo > 100) throw new IllegalArgumentException("attemptNo 必须在 0..100");
        Objects.requireNonNull(state, "state");
        if (version < 0) throw new IllegalArgumentException("step version 不得为负数");
    }
}
