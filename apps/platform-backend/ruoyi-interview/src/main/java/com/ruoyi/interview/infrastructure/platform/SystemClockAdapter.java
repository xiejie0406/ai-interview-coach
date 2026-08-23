package com.ruoyi.interview.infrastructure.platform;

import com.ruoyi.interview.application.platform.port.ClockPort;

import java.time.Clock;
import java.time.Instant;

/** 生产时钟 adapter；Clock 可由 Composition Root 注入，避免业务代码直接调用 Instant.now。 */
public final class SystemClockAdapter implements ClockPort {

    private final Clock clock;

    public SystemClockAdapter(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}

