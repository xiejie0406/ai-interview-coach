package com.ruoyi.aps.domain.resource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import com.ruoyi.aps.domain.shared.UtcTimeWindow;

/** 资源绝对时间窗；所有边界均为 UTC 半开区间。 */
public record AvailabilityWindow(String id, String resourceId, AvailabilityType type, UtcTimeWindow window,
        BigDecimal capacityRatio, String sourceType, String sourceRef, String reason)
{
    public AvailabilityWindow
    {
        requireText(id, "时间窗 ID");
        requireText(resourceId, "资源 ID");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(capacityRatio, "capacityRatio");
        if (capacityRatio.signum() <= 0 || capacityRatio.compareTo(BigDecimal.ONE) > 0)
        {
            throw new IllegalArgumentException("容量比例必须在 (0,1] 内");
        }
        requireText(sourceType, "来源类型");
    }

    public boolean covers(Instant start, Instant end)
    {
        return !window.startInclusive().isAfter(start) && !window.endExclusive().isBefore(end);
    }

    private static void requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
