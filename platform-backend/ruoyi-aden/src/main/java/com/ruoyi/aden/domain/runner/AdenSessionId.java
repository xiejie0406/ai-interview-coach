package com.ruoyi.aden.domain.runner;

public record AdenSessionId(String value) {
    public AdenSessionId { value = AdenRunnerIds.requireUuid(value, "sessionId"); }
}
