package com.ruoyi.aden.domain.runner;

public record AdenRunnerId(String value) {
    public AdenRunnerId { value = AdenRunnerIds.requireUuid(value, "runnerId"); }
}
