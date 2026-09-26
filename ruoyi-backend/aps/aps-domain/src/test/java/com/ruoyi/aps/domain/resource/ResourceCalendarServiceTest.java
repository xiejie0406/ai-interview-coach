package com.ruoyi.aps.domain.resource;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.domain.shared.UtcTimeWindow;
import org.junit.jupiter.api.Test;

class ResourceCalendarServiceTest
{
    private final ResourceCalendarService service = new ResourceCalendarService();

    @Test
    void leaveAndMaintenanceSubtractFromProductiveWindowsWithoutDoubleCountingSkills()
    {
        Instant eight = Instant.parse("2026-09-14T00:00:00Z");
        Instant twelve = Instant.parse("2026-09-14T04:00:00Z");
        Instant thirteen = Instant.parse("2026-09-14T05:00:00Z");
        Instant seventeen = Instant.parse("2026-09-14T09:00:00Z");
        List<AvailabilityWindow> windows = List.of(
                window("a", AvailabilityType.AVAILABLE, eight, seventeen),
                window("b", AvailabilityType.LEAVE, twelve, thirteen));

        assertThat(service.compile(eight, seventeen, windows)).containsExactly(
                new ResourceCalendarService.NetWindow(eight, twelve, BigDecimal.ONE),
                new ResourceCalendarService.NetWindow(thirteen, seventeen, BigDecimal.ONE));
    }

    @Test
    void levelThreeCandidateRejectsLevelTwoAndLeaveButKeepsActiveQualifiedResource()
    {
        Instant start = Instant.parse("2026-09-14T01:00:00Z");
        Instant end = Instant.parse("2026-09-14T02:00:00Z");
        ResourceSkill levelTwo = skill("s1", 2);
        ResourceSkill levelThree = skill("s2", 3);
        AvailabilityWindow available = window("a", AvailabilityType.AVAILABLE,
                Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-14T08:00:00Z"));
        AvailabilityWindow leave = window("l", AvailabilityType.LEAVE, start, end);

        assertThat(service.isQualified(ResourceStatus.ACTIVE, List.of(levelTwo), List.of(available),
                "WELD", 3, start, end)).isFalse();
        assertThat(service.isQualified(ResourceStatus.ACTIVE, List.of(levelThree), List.of(available, leave),
                "WELD", 3, start, end)).isFalse();
        assertThat(service.isQualified(ResourceStatus.ACTIVE, List.of(levelThree), List.of(available),
                "WELD", 3, start, end)).isTrue();
    }

    @Test
    void materializedWorkshopClosureOverridesPersonalShiftUntilApprovedOvertimeWindow()
    {
        Instant eight = Instant.parse("2026-09-14T00:00:00Z");
        Instant seventeen = Instant.parse("2026-09-14T09:00:00Z");
        Instant nineteen = Instant.parse("2026-09-14T11:00:00Z");
        AvailabilityWindow personalShift = window("shift", AvailabilityType.AVAILABLE, eight, seventeen);
        AvailabilityWindow workshopClosed = window("closed", AvailabilityType.MAINTENANCE, eight, seventeen);
        AvailabilityWindow approvedOvertime = window("overtime", AvailabilityType.OVERTIME, seventeen, nineteen);

        assertThat(service.compile(eight, seventeen, List.of(personalShift, workshopClosed))).isEmpty();
        assertThat(service.compile(eight, nineteen,
                List.of(personalShift, workshopClosed, approvedOvertime))).containsExactly(
                        new ResourceCalendarService.NetWindow(seventeen, nineteen, BigDecimal.ONE));
    }

    private AvailabilityWindow window(String id, AvailabilityType type, Instant start, Instant end)
    {
        return new AvailabilityWindow(id, "r1", type, new UtcTimeWindow(start, end), BigDecimal.ONE,
                "MANUAL", null, null);
    }

    private ResourceSkill skill(String id, int level)
    {
        return new ResourceSkill(id, "r1", "WELD", "焊接", level, null, null, null, "ACTIVE");
    }
}
