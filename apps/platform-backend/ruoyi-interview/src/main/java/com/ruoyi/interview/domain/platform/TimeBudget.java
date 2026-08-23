package com.ruoyi.interview.domain.platform;

import java.time.Duration;

/** 确定性的时间上限，Provider 和 Agent 不可自行增加。 */
public record TimeBudget(Duration duration) {

    public TimeBudget {
        DomainPreconditions.requireNonNull(duration, "duration");
        DomainPreconditions.require(!duration.isNegative() && !duration.isZero(), DomainErrorCode.INVALID_ARGUMENT,
                "time budget must be positive");
    }
}
