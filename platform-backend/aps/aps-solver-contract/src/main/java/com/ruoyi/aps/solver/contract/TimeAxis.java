package com.ruoyi.aps.solver.contract;

import java.time.Duration;
import java.time.Instant;

/** 把外部 UTC 时间确定性编译为相对 horizon 的非负整数时间单位。 */
public record TimeAxis(Instant horizonStart, Instant horizonEnd, int unitSeconds)
{
    public TimeAxis
    {
        if (horizonStart == null || horizonEnd == null || !horizonEnd.isAfter(horizonStart))
            throw new IllegalArgumentException("horizon 必须是正向区间");
        if (unitSeconds != 1 && unitSeconds != 60) throw new IllegalArgumentException("时间单位只允许 1 或 60 秒");
    }

    public int floorOffset(Instant value)
    {
        long seconds = Duration.between(horizonStart, value).getSeconds();
        if (seconds < 0) throw new IllegalArgumentException("时间不能早于 horizon");
        return Math.toIntExact(seconds / unitSeconds);
    }

    public int ceilOffset(Instant value)
    {
        long millis = Duration.between(horizonStart, value).toMillis();
        if (millis < 0) throw new IllegalArgumentException("时间不能早于 horizon");
        long unitMillis = unitSeconds * 1000L;
        return Math.toIntExact((millis + unitMillis - 1L) / unitMillis);
    }

    public int horizonUnits()
    {
        return floorOffset(horizonEnd);
    }

    public Instant instantAt(long offset)
    {
        if (offset < 0 || offset > horizonUnits()) throw new IllegalArgumentException("相对时间超出 horizon");
        return horizonStart.plusSeconds(Math.multiplyExact(offset, unitSeconds));
    }

    public int ceilDurationSeconds(long seconds)
    {
        if (seconds < 0) throw new IllegalArgumentException("时长不能为负数");
        return Math.toIntExact((seconds + unitSeconds - 1L) / unitSeconds);
    }
}
