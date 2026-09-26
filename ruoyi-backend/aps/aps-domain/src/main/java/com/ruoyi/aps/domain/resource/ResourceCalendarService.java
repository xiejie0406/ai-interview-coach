package com.ruoyi.aps.domain.resource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import com.ruoyi.aps.domain.shared.UtcTimeWindow;

/** 不依赖数据库或求解器的资源净日历规则。 */
public final class ResourceCalendarService
{
    public List<NetWindow> compile(Instant rangeStart, Instant rangeEnd, List<AvailabilityWindow> windows)
    {
        UtcTimeWindow range = new UtcTimeWindow(rangeStart, rangeEnd);
        TreeSet<Instant> points = new TreeSet<>();
        points.add(range.startInclusive());
        points.add(range.endExclusive());
        for (AvailabilityWindow value : windows)
        {
            Instant start = value.window().startInclusive().isBefore(range.startInclusive())
                    ? range.startInclusive() : value.window().startInclusive();
            Instant end = value.window().endExclusive().isAfter(range.endExclusive())
                    ? range.endExclusive() : value.window().endExclusive();
            if (end.isAfter(start))
            {
                points.add(start);
                points.add(end);
            }
        }

        List<Instant> boundaries = new ArrayList<>(points);
        List<NetWindow> result = new ArrayList<>();
        for (int index = 0; index < boundaries.size() - 1; index++)
        {
            Instant start = boundaries.get(index);
            Instant end = boundaries.get(index + 1);
            boolean blocked = windows.stream().anyMatch(value -> !value.type().productive() && value.covers(start, end));
            BigDecimal ratio = windows.stream()
                    .filter(value -> value.type().productive() && value.covers(start, end))
                    .map(AvailabilityWindow::capacityRatio)
                    .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            if (!blocked && ratio.signum() > 0)
            {
                appendMerged(result, new NetWindow(start, end, ratio));
            }
        }
        return List.copyOf(result);
    }

    public boolean isQualified(ResourceStatus resourceStatus, List<ResourceSkill> skills,
            List<AvailabilityWindow> windows, String requiredSkill, int minimumLevel, Instant start, Instant end)
    {
        if (resourceStatus != ResourceStatus.ACTIVE
                || skills.stream().noneMatch(skill -> skill.qualifies(requiredSkill, minimumLevel, start)))
        {
            return false;
        }
        return compile(start, end, windows).stream()
                .anyMatch(window -> !window.start().isAfter(start) && !window.end().isBefore(end));
    }

    private void appendMerged(List<NetWindow> result, NetWindow next)
    {
        if (!result.isEmpty())
        {
            NetWindow last = result.get(result.size() - 1);
            if (last.end().equals(next.start()) && last.capacityRatio().compareTo(next.capacityRatio()) == 0)
            {
                result.set(result.size() - 1, new NetWindow(last.start(), next.end(), last.capacityRatio()));
                return;
            }
        }
        result.add(next);
    }

    public record NetWindow(Instant start, Instant end, BigDecimal capacityRatio)
    {
    }
}
