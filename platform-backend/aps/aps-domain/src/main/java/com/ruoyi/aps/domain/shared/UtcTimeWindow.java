package com.ruoyi.aps.domain.shared;

import java.time.Instant;
import java.util.Objects;

/**
 * UTC 半开时间窗 {@code [startInclusive, endExclusive)}。
 */
public record UtcTimeWindow(Instant startInclusive, Instant endExclusive)
{
    public UtcTimeWindow
    {
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endExclusive, "endExclusive");
        if (!endExclusive.isAfter(startInclusive))
        {
            throw new IllegalArgumentException("时间窗结束时刻必须晚于开始时刻");
        }
    }

    public boolean contains(Instant instant)
    {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }

    public boolean overlaps(UtcTimeWindow other)
    {
        Objects.requireNonNull(other, "other");
        return startInclusive.isBefore(other.endExclusive) && other.startInclusive.isBefore(endExclusive);
    }
}
