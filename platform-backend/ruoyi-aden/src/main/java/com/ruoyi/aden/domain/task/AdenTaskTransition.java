package com.ruoyi.aden.domain.task;

import java.util.Objects;

public record AdenTaskTransition(AdenTask task, AdenTaskStateChanged event) {
    public AdenTaskTransition {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(event, "event");
    }
}
