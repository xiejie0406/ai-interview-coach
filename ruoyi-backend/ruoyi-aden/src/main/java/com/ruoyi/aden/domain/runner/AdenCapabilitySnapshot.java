package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.task.AdenCapabilityCode;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record AdenCapabilitySnapshot(Set<AdenCapabilityCode> capabilities, Instant capturedAt) {
    public AdenCapabilitySnapshot {
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (capabilities.isEmpty()) throw new IllegalArgumentException("capabilities 不能为空");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public boolean supports(AdenCapabilityCode capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }
}
