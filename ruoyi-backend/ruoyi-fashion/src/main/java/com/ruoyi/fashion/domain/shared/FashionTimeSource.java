package com.ruoyi.fashion.domain.shared;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

/** 统一产生 UTC Instant；展示时区只能在传输/页面边界转换。 */
@FashionModuleEnabled
@Component
public final class FashionTimeSource {
    private final Clock clock;

    public FashionTimeSource() {
        this(Clock.systemUTC());
    }

    FashionTimeSource(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }
}
