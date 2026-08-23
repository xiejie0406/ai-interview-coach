package com.ruoyi.interview.application.platform.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockPort {

    Instant now();
}
