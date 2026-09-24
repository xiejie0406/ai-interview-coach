package com.ruoyi.aps.application.reporting;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ruoyi.aps.application.reporting.ReportingRepository.ActualOccupancyFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.AvailabilityFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.OrderTaskFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.PersonFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.PlanReference;
import com.ruoyi.aps.application.reporting.ReportingRepository.PlanSegmentFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.PlannedLaborFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.ProductionReportFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.QuantityFact;
import com.ruoyi.aps.application.reporting.ReportingRepository.SkillQualification;
import com.ruoyi.aps.application.reporting.ReportingRepository.TaskIdentity;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
class ReportingServiceTest
{
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final ResourceAccessScope SCOPE = new ResourceAccessScope("manager", false);
    private static final Instant NOW = at("2026-09-15T18:00:00+08:00");

    @Test
    void dailyReportClipsCrossDaySegmentsKeepsFrozenDenominatorAndAllocatesSharedBatchOnce()
    {
        StubRepository repository = new StubRepository();
        repository.baseline = new PlanReference("baseline", "日冻结", NOW, LocalDate.of(2026, 9, 15));
        repository.current = new PlanReference("current", "当前", NOW, null);
        TaskIdentity task = identity("task-a", "PCS", at("2026-09-15T17:00:00+08:00"));
        PlanSegmentFact baseline = plan(task, "baseline", "segment-b", "allocation-b", "MACHINE", "furnace",
                at("2026-09-14T22:00:00+08:00"), at("2026-09-15T10:00:00+08:00"),
                at("2026-09-15T10:00:00+08:00"), "200", "100", "200");
        PlanSegmentFact current = plan(task, "current", "segment-c", "allocation-c", "MACHINE", "furnace",
                at("2026-09-15T06:00:00+08:00"), at("2026-09-15T10:00:00+08:00"),
                at("2026-09-15T10:00:00+08:00"), "160", "80", "160");
        repository.planFacts = Map.of("baseline", List.of(baseline), "current", List.of(current));
        repository.actuals = List.of(
                new ActualOccupancyFact(task, "occupancy", "furnace", "MACHINE",
                        at("2026-09-15T08:00:00+08:00"), at("2026-09-15T10:00:00+08:00"),
                        bd("80"), bd("160")));
        repository.reports = List.of(
                new ProductionReportFact(task, "report", at("2026-09-15T10:00:00+08:00"),
                        bd("80"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        repository.quantities = List.of(
                new QuantityFact(task, "release", "QUALITY_RELEASE", null, "PENDING_QUALITY", "AVAILABLE",
                        at("2026-09-15T10:00:00+08:00"), bd("80")));

        var report = service(repository).dailyProduction(SCOPE, LocalDate.of(2026, 9, 15), null, null);

        assertThat(report.metadata().baselinePlanVersionId()).isEqualTo("baseline");
        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.baselinePlannedQty()).isEqualByComparingTo("100");
            assertThat(row.currentPlannedQty()).isEqualByComparingTo("80");
            assertThat(row.baselineMachineHours()).isEqualByComparingTo("5");
            assertThat(row.currentMachineHours()).isEqualByComparingTo("2");
            assertThat(row.actualMachineHours()).isEqualByComparingTo("1");
            assertThat(row.actualGoodQty()).isEqualByComparingTo("80");
            assertThat(row.achievementRatio()).isEqualByComparingTo("0.8");
            assertThat(row.carryoverQty()).isZero();
        });
    }

    @Test
    void laborReportDeduplicatesMultiSkillPersonButKeepsAfternoonPeakShortage()
    {
        StubRepository repository = new StubRepository();
        repository.current = new PlanReference("current", "当前", NOW, null);
        repository.people = List.of(new PersonFact("person-1", "P1", "张工", "ws", "WS", "一车间",
                "wc", "WC", "焊接", "A", List.of(skill("WELD", null, null),
                        skill("INSPECT", null, null))));
        repository.availability = List.of(
                new AvailabilityFact("person-1", "AVAILABLE", at("2026-09-15T08:00:00+08:00"),
                        at("2026-09-15T16:00:00+08:00"), BigDecimal.ONE));
        repository.plannedLabor = List.of(
                        new PlannedLaborFact("segment-1", "allocation-1", "person-1", "WELD",
                                at("2026-09-15T13:00:00+08:00"), at("2026-09-15T15:00:00+08:00")),
                        new PlannedLaborFact("segment-1", "allocation-2", "person-1", "WELD",
                                at("2026-09-15T13:00:00+08:00"), at("2026-09-15T15:00:00+08:00")));

        var report = service(repository).laborCapacity(SCOPE, LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 15), null, null, null);

        assertThat(report.people()).singleElement().satisfies(row -> {
            assertThat(row.availableHours()).isEqualByComparingTo("8");
            assertThat(row.scheduledHours()).isEqualByComparingTo("2");
            assertThat(row.skillCodes()).containsExactly("INSPECT", "WELD");
        });
        assertThat(report.skills()).filteredOn(row -> row.skillCode().equals("WELD")).singleElement().satisfies(row -> {
            assertThat(row.availablePotentialHours()).isEqualByComparingTo("8");
            assertThat(row.scheduledHours()).isEqualByComparingTo("4");
            assertThat(row.peakRequiredPeople()).isEqualTo(2);
            assertThat(row.peakQualifiedPeople()).isEqualTo(1);
            assertThat(row.peakShortagePeople()).isEqualTo(1);
            assertThat(row.reasonCodes()).contains("PEAK_SKILL_SHORTAGE", "SKILL_POTENTIAL_NON_ADDITIVE");
        });
    }

    @Test
    void laborReportUsesSkillValidityForPotentialAndPeakQualification()
    {
        StubRepository repository = new StubRepository();
        repository.current = new PlanReference("current", "当前", NOW, null);
        repository.people = List.of(new PersonFact("person-1", "P1", "张工", "ws", "WS", "一车间",
                "wc", "WC", "焊接", "A", List.of(skill("WELD",
                        at("2026-09-15T08:00:00+08:00"), at("2026-09-15T12:00:00+08:00")))));
        repository.availability = List.of(new AvailabilityFact("person-1", "AVAILABLE",
                at("2026-09-15T08:00:00+08:00"), at("2026-09-15T16:00:00+08:00"), BigDecimal.ONE));
        repository.plannedLabor = List.of(new PlannedLaborFact("segment-1", "allocation-1", "person-1", "WELD",
                at("2026-09-15T13:00:00+08:00"), at("2026-09-15T15:00:00+08:00")));

        var report = service(repository).laborCapacity(SCOPE, LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 15), null, null, "WELD");

        assertThat(report.skills()).singleElement().satisfies(row -> {
            assertThat(row.availablePotentialHours()).isEqualByComparingTo("4");
            assertThat(row.peakRequiredPeople()).isEqualTo(1);
            assertThat(row.peakQualifiedPeople()).isZero();
            assertThat(row.peakShortagePeople()).isEqualTo(1);
        });
    }

    @Test
    void etaIsUnknownWhenAnyRequiredTaskIsUnplannedAndKeepsKnownLowerBound()
    {
        StubRepository repository = new StubRepository();
        repository.current = new PlanReference("current", "当前", NOW, null);
        repository.orderTasks = List.of(
                orderTask("line-a", 1, "task-a", true, at("2026-09-15T09:00:00+08:00"), "100"),
                orderTask("line-b", 2, "task-b", true, null, "0"));

        var report = service(repository).orderDelivery(SCOPE, null);

        assertThat(report.orders()).singleElement().satisfies(order -> {
            assertThat(order.etaState()).isEqualTo("UNKNOWN");
            assertThat(order.expectedProductionAt()).isNull();
            assertThat(order.knownLowerBoundAt()).isEqualTo(at("2026-09-15T09:00:00+08:00"));
            assertThat(order.reasons()).extracting(value -> value.code()).contains("UNPLANNED_TASK");
        });
    }

    @Test
    void etaIsUnknownWhenWorkshopScopeHidesPartOfAnOrderLine()
    {
        StubRepository repository = new StubRepository();
        repository.current = new PlanReference("current", "当前", NOW, null);
        repository.orderTasks = List.of(orderTask("line-a", 1, "task-a", true,
                at("2026-09-15T09:00:00+08:00"), "0", 2));

        var report = service(repository).orderDelivery(SCOPE, null);

        assertThat(report.orders()).singleElement().satisfies(order -> {
            assertThat(order.etaState()).isEqualTo("UNKNOWN");
            assertThat(order.expectedProductionAt()).isNull();
            assertThat(order.reasons()).extracting(value -> value.code()).contains("SCOPE_PARTIAL");
        });
    }

    @Test
    void o100EtaAndOrderCompletionRequireBothProductLines()
    {
        StubRepository repository = new StubRepository();
        repository.current = new PlanReference("current", "当前", NOW, null);
        Instant aFinishedAt = at("2026-09-14T17:00:00+08:00");
        Instant bFinishedAt = at("2026-09-15T09:00:00+08:00");
        repository.orderTasks = List.of(
                o100Task("line-a", 1, "task-a30", "IN_PRODUCTION", "COMPLETED",
                        aFinishedAt, "100", aFinishedAt),
                o100Task("line-b", 2, "task-b30", "IN_PRODUCTION", "PLANNED",
                        bFinishedAt, "0", null));

        var whileBPending = service(repository).orderDelivery(SCOPE, "order").orders().get(0);
        assertThat(whileBPending.expectedProductionAt()).isEqualTo(bFinishedAt);
        assertThat(whileBPending.productionCompleted()).isFalse();
        assertThat(whileBPending.orderClosed()).isFalse();

        repository.orderTasks = List.of(
                o100Task("line-a", 1, "task-a30", "IN_PRODUCTION", "COMPLETED",
                        aFinishedAt, "100", aFinishedAt),
                o100Task("line-b", 2, "task-b30", "IN_PRODUCTION", "COMPLETED",
                        bFinishedAt, "100", bFinishedAt));
        var productionFinished = service(repository).orderDelivery(SCOPE, "order").orders().get(0);
        assertThat(productionFinished.expectedProductionAt()).isEqualTo(bFinishedAt);
        assertThat(productionFinished.productionCompleted()).isTrue();
        assertThat(productionFinished.orderClosed()).isFalse();

        repository.orderTasks = List.of(
                o100Task("line-a", 1, "task-a30", "COMPLETED", "COMPLETED",
                        aFinishedAt, "100", aFinishedAt),
                o100Task("line-b", 2, "task-b30", "COMPLETED", "COMPLETED",
                        bFinishedAt, "100", bFinishedAt));
        assertThat(service(repository).orderDelivery(SCOPE, "order").orders().get(0).orderClosed()).isTrue();
    }

    private ReportingService service(ReportingRepository repository)
    {
        return new ReportingService(repository, "SITE_01", ZONE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlanSegmentFact plan(TaskIdentity task, String version, String segment, String allocation,
            String role, String resource, Instant start, Instant end, Instant releaseAt, String releaseQty,
            String memberQty, String jobQty)
    {
        return new PlanSegmentFact(task, version, "job", segment, allocation, role, resource, start, end,
                releaseAt, bd(releaseQty), bd(memberQty), bd(jobQty));
    }

    private static TaskIdentity identity(String taskId, String uom, Instant promised)
    {
        return new TaskIdentity("ws", "WS", "一车间", "wc", "WC", "加工中心", "op", "OP", "加工",
                "order", "O-100", "line", 1, "item", "A", "产品A", "lot", "LOT-A", taskId,
                taskId.toUpperCase(), "任务", "PLANNED", uom, promised);
    }

    private static OrderTaskFact orderTask(String lineId, int lineNo, String taskId, boolean terminal,
            Instant plannedEnd, String released)
    {
        return orderTask(lineId, lineNo, taskId, terminal, plannedEnd, released, 0);
    }

    private static OrderTaskFact orderTask(String lineId, int lineNo, String taskId, boolean terminal,
            Instant plannedEnd, String released, int hiddenScopeTaskCount)
    {
        return new OrderTaskFact("order", "O-100", "IN_PRODUCTION", at("2026-09-15T17:00:00+08:00"),
                lineId, lineNo, "IN_PRODUCTION", at("2026-09-15T17:00:00+08:00"), "item-" + lineNo,
                "ITEM-" + lineNo, "产品" + lineNo, bd("100"), "PCS", taskId, taskId.toUpperCase(),
                "PLANNED", terminal, plannedEnd, bd(released), null, 0, 0, false, false,
                hiddenScopeTaskCount);
    }

    private static OrderTaskFact o100Task(String lineId, int lineNo, String taskId,
            String orderStatus, String taskStatus, Instant plannedEnd, String released, Instant releasedAt)
    {
        return new OrderTaskFact("order", "O-100", orderStatus, at("2026-09-14T17:00:00+08:00"),
                lineId, lineNo, "IN_PRODUCTION", at("2026-09-14T17:00:00+08:00"), "item-" + lineNo,
                "ITEM-" + lineNo, "产品" + lineNo, bd("100"), "PCS", taskId, taskId.toUpperCase(),
                taskStatus, true, plannedEnd, bd(released), releasedAt, 0, 0, false, false, 0);
    }

    private static Instant at(String value) { return Instant.parse(java.time.OffsetDateTime.parse(value).toInstant().toString()); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static SkillQualification skill(String code, Instant fromAt, Instant toAt)
    {
        return new SkillQualification(code, fromAt, toAt);
    }

    private static final class StubRepository implements ReportingRepository
    {
        private PlanReference baseline;
        private PlanReference current;
        private Map<String, List<PlanSegmentFact>> planFacts = Map.of();
        private List<ActualOccupancyFact> actuals = List.of();
        private List<ProductionReportFact> reports = List.of();
        private List<QuantityFact> quantities = List.of();
        private List<PersonFact> people = List.of();
        private List<AvailabilityFact> availability = List.of();
        private List<PlannedLaborFact> plannedLabor = List.of();
        private List<ReportingRepository.UnplannedLaborFact> unplannedLabor = List.of();
        private List<OrderTaskFact> orderTasks = List.of();

        @Override public Optional<PlanReference> findDailyBaseline(LocalDate date) { return Optional.ofNullable(baseline); }
        @Override public Optional<PlanReference> findCurrentPublished() { return Optional.ofNullable(current); }
        @Override public List<PlanSegmentFact> listPlanSegmentFacts(ResourceAccessScope scope, String version,
                Instant from, Instant to, String workshop, String center)
        { return planFacts.getOrDefault(version, List.of()); }
        @Override public List<ActualOccupancyFact> listActualOccupancyFacts(ResourceAccessScope scope,
                Instant from, Instant to, String workshop, String center) { return actuals; }
        @Override public List<ProductionReportFact> listProductionReportFacts(ResourceAccessScope scope,
                Instant from, Instant to, String workshop, String center) { return reports; }
        @Override public List<QuantityFact> listQuantityFacts(ResourceAccessScope scope, Instant from,
                Instant to, String workshop, String center) { return quantities; }
        @Override public List<PersonFact> listPeople(ResourceAccessScope scope, Instant from, Instant to,
                String workshop, String center)
        { return people; }
        @Override public List<AvailabilityFact> listAvailability(ResourceAccessScope scope, Instant from,
                Instant to, String workshop, String center) { return availability; }
        @Override public List<PlannedLaborFact> listPlannedLabor(ResourceAccessScope scope, String version,
                Instant from, Instant to, String workshop, String center, String skill) { return plannedLabor; }
        @Override public List<ReportingRepository.UnplannedLaborFact> listUnplannedLabor(ResourceAccessScope scope,
                String version, String workshop, String center, String skill) { return unplannedLabor; }
        @Override public List<OrderTaskFact> listOrderTaskFacts(ResourceAccessScope scope, String version,
                String order) { return orderTasks; }
    }
}
