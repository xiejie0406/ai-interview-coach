package com.ruoyi.aps.application.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.reporting.ReportingCatalog.DailyProductionReport;
import com.ruoyi.aps.application.reporting.ReportingCatalog.DailyTaskRow;
import com.ruoyi.aps.application.reporting.ReportingCatalog.DeliveryReason;
import com.ruoyi.aps.application.reporting.ReportingCatalog.LaborCapacityReport;
import com.ruoyi.aps.application.reporting.ReportingCatalog.OrderDeliveryReport;
import com.ruoyi.aps.application.reporting.ReportingCatalog.OrderDeliveryRow;
import com.ruoyi.aps.application.reporting.ReportingCatalog.OrderLineDeliveryRow;
import com.ruoyi.aps.application.reporting.ReportingCatalog.PersonCapacityRow;
import com.ruoyi.aps.application.reporting.ReportingCatalog.ReportMetadata;
import com.ruoyi.aps.application.reporting.ReportingCatalog.SkillCapacityRow;
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
import com.ruoyi.aps.application.reporting.ReportingRepository.UnplannedLaborFact;
import com.ruoyi.aps.application.resource.ResourceAccessScope;

/** REQ-08/09 可复算报表与 ETA 用例，不创建第二套事实。 */
public final class ReportingService
{
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final String POTENTIAL_NOTICE =
            "同一人员可出现在多个技能潜力行，但跨技能汇总不可相加；人员总可用与已排占用按资源时间并集去重。";
    private final ReportingRepository repository;
    private final String siteCode;
    private final ZoneId zoneId;
    private final Clock clock;

    public ReportingService(ReportingRepository repository, String siteCode, ZoneId zoneId)
    {
        this(repository, siteCode, zoneId, Clock.systemUTC());
    }

    ReportingService(ReportingRepository repository, String siteCode, ZoneId zoneId, Clock clock)
    {
        this.repository = Objects.requireNonNull(repository);
        this.siteCode = required(siteCode, "APS 站点编码未配置");
        this.zoneId = Objects.requireNonNull(zoneId);
        this.clock = Objects.requireNonNull(clock);
    }

    public DailyProductionReport dailyProduction(ResourceAccessScope scope, LocalDate businessDate,
            String workshopId, String workCenterId)
    {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(businessDate);
        Instant fromAt = businessDate.atStartOfDay(zoneId).toInstant();
        Instant toAt = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant cutoff = clock.instant();
        PlanReference baseline = repository.findDailyBaseline(businessDate).orElse(null);
        PlanReference current = repository.findCurrentPublished().orElse(null);
        Map<String, DailyAccumulator> rows = new LinkedHashMap<>();
        if (baseline != null)
            addPlan(rows, repository.listPlanSegmentFacts(scope, baseline.id(), fromAt, toAt,
                    workshopId, workCenterId), true, fromAt, toAt);
        if (current != null)
            addPlan(rows, repository.listPlanSegmentFacts(scope, current.id(), fromAt, toAt,
                    workshopId, workCenterId), false, fromAt, toAt);
        addActualOccupancy(rows, repository.listActualOccupancyFacts(scope, fromAt, toAt,
                workshopId, workCenterId), fromAt, toAt, cutoff);
        addReports(rows, repository.listProductionReportFacts(scope, fromAt, toAt,
                workshopId, workCenterId));
        addQuantities(rows, repository.listQuantityFacts(scope, fromAt, toAt,
                workshopId, workCenterId));
        List<DailyTaskRow> result = rows.values().stream().map(value -> value.row(baseline != null))
                .sorted(Comparator.comparing((DailyTaskRow value) -> nullSafe(value.workshopCode()))
                        .thenComparing(value -> nullSafe(value.workCenterCode()))
                        .thenComparing(DailyTaskRow::orderNo)
                        .thenComparingInt(DailyTaskRow::lineNo)
                        .thenComparing(DailyTaskRow::taskCode))
                .toList();
        return new DailyProductionReport(businessDate,
                metadata(fromAt, toAt, baseline, current, cutoff), result);
    }

    public LaborCapacityReport laborCapacity(ResourceAccessScope scope, LocalDate fromDate, LocalDate toDate,
            String workshopId, String workCenterId, String skillCode)
    {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(fromDate);
        Objects.requireNonNull(toDate);
        if (toDate.isBefore(fromDate)) invalid("产能报表结束日期不能早于开始日期");
        if (Duration.between(fromDate.atStartOfDay(zoneId), toDate.plusDays(1).atStartOfDay(zoneId)).toDays() > 31)
            invalid("产能报表单次最多查询 31 个业务日");
        Instant fromAt = fromDate.atStartOfDay(zoneId).toInstant();
        Instant toAt = toDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant cutoff = clock.instant();
        PlanReference current = repository.findCurrentPublished().orElse(null);
        List<PersonFact> people = repository.listPeople(scope, fromAt, toAt, workshopId, workCenterId);
        List<AvailabilityFact> availability = repository.listAvailability(scope, fromAt, toAt,
                workshopId, workCenterId);
        List<PlannedLaborFact> planned = current == null ? List.of()
                : repository.listPlannedLabor(scope, current.id(), fromAt, toAt,
                        workshopId, workCenterId, blankToNull(skillCode));
        List<UnplannedLaborFact> unplanned = repository.listUnplannedLabor(scope,
                current == null ? null : current.id(), workshopId, workCenterId, blankToNull(skillCode));

        Map<String, List<AvailabilityFact>> availabilityByPerson = groupAvailability(availability);
        Map<String, List<Interval>> scheduledByPerson = new LinkedHashMap<>();
        for (PlannedLaborFact fact : planned)
            scheduledByPerson.computeIfAbsent(fact.resourceId(), ignored -> new ArrayList<>())
                    .add(new Interval(max(fact.startAt(), fromAt), min(fact.endAt(), toAt)));

        List<PersonCapacityRow> personRows = new ArrayList<>();
        Map<String, BigDecimal> availableByPerson = new LinkedHashMap<>();
        Map<String, BigDecimal> scheduledHoursByPerson = new LinkedHashMap<>();
        for (PersonFact person : people)
        {
            BigDecimal availableHours = availabilityHours(availabilityByPerson.getOrDefault(person.resourceId(),
                    List.of()), fromAt, toAt);
            BigDecimal scheduledHours = hours(mergedSeconds(scheduledByPerson.getOrDefault(person.resourceId(),
                    List.of())));
            BigDecimal remaining = availableHours.subtract(scheduledHours).max(BigDecimal.ZERO);
            availableByPerson.put(person.resourceId(), availableHours);
            scheduledHoursByPerson.put(person.resourceId(), scheduledHours);
            personRows.add(new PersonCapacityRow(person.resourceId(), person.resourceCode(), person.resourceName(),
                    person.workshopId(), person.workshopCode(), person.workshopName(), person.workCenterId(),
                    person.workCenterCode(), person.workCenterName(), person.teamName(), person.skillCodes(),
                    availableHours, scheduledHours, remaining, scheduledHours.compareTo(availableHours) > 0));
        }
        personRows.sort(Comparator.comparing(PersonCapacityRow::workshopCode)
                .thenComparing(value -> nullSafe(value.workCenterCode()))
                .thenComparing(PersonCapacityRow::resourceCode));

        Set<String> skills = new TreeSet<>();
        people.forEach(person -> skills.addAll(person.skillCodes()));
        planned.stream().map(PlannedLaborFact::skillCode).filter(Objects::nonNull).forEach(skills::add);
        unplanned.stream().map(UnplannedLaborFact::skillCode).filter(Objects::nonNull).forEach(skills::add);
        if (skillCode != null && !skillCode.isBlank()) skills.removeIf(value -> !value.equals(skillCode));
        List<SkillCapacityRow> skillRows = new ArrayList<>();
        for (String skill : skills)
        {
            BigDecimal potential = people.stream().filter(value -> value.skillCodes().contains(skill))
                    .map(value -> qualificationAvailabilityHours(value, skill,
                            availabilityByPerson.getOrDefault(value.resourceId(), List.of()), fromAt, toAt))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal scheduled = planned.stream().filter(value -> skill.equals(value.skillCode()))
                    .map(value -> hours(seconds(max(value.startAt(), fromAt), min(value.endAt(), toAt))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pending = unplanned.stream().filter(value -> skill.equals(value.skillCode()))
                    .map(value -> hours(value.requiredSeconds()).multiply(BigDecimal.valueOf(value.requiredPeople())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Peak peak = peak(skill, people, availabilityByPerson, planned, fromAt, toAt);
            List<String> reasons = new ArrayList<>();
            if (peak.shortage() > 0) reasons.add("PEAK_SKILL_SHORTAGE");
            if (pending.signum() > 0) reasons.add("UNPLANNED_SKILL_DEMAND");
            reasons.add("SKILL_POTENTIAL_NON_ADDITIVE");
            skillRows.add(new SkillCapacityRow(skill, potential, scheduled, pending, peak.required(),
                    peak.qualified(), peak.shortage(), peak.at(), reasons));
        }
        return new LaborCapacityReport(metadata(fromAt, toAt, null, current, cutoff), personRows, skillRows,
                POTENTIAL_NOTICE);
    }

    public OrderDeliveryReport orderDelivery(ResourceAccessScope scope, String orderId)
    {
        Objects.requireNonNull(scope);
        Instant cutoff = clock.instant();
        PlanReference current = repository.findCurrentPublished().orElse(null);
        List<OrderTaskFact> facts = repository.listOrderTaskFacts(scope, current == null ? null : current.id(),
                blankToNull(orderId));
        Map<String, List<OrderTaskFact>> byOrder = new LinkedHashMap<>();
        facts.forEach(value -> byOrder.computeIfAbsent(value.orderId(), ignored -> new ArrayList<>()).add(value));
        List<OrderDeliveryRow> orders = byOrder.values().stream().map(this::orderRow)
                .sorted(Comparator.comparing(OrderDeliveryRow::orderNo)).toList();
        return new OrderDeliveryReport(metadata(null, null, null, current, cutoff), orders);
    }

    private void addPlan(Map<String, DailyAccumulator> rows, List<PlanSegmentFact> facts, boolean baseline,
            Instant fromAt, Instant toAt)
    {
        for (PlanSegmentFact fact : facts)
        {
            DailyAccumulator row = rows.computeIfAbsent(fact.identity().taskId(), ignored ->
                    new DailyAccumulator(fact.identity()));
            BigDecimal ratio = ratio(fact.memberQty(), fact.jobQty());
            row.plan(fact, baseline, ratio, fromAt, toAt);
        }
    }

    private void addActualOccupancy(Map<String, DailyAccumulator> rows, List<ActualOccupancyFact> facts,
            Instant fromAt, Instant toAt, Instant cutoff)
    {
        for (ActualOccupancyFact fact : facts)
        {
            DailyAccumulator row = rows.computeIfAbsent(fact.identity().taskId(), ignored ->
                    new DailyAccumulator(fact.identity()));
            row.actual(fact, ratio(fact.memberQty(), fact.jobQty()), fromAt, toAt, cutoff);
        }
    }

    private void addReports(Map<String, DailyAccumulator> rows, List<ProductionReportFact> facts)
    {
        for (ProductionReportFact fact : facts)
            rows.computeIfAbsent(fact.identity().taskId(), ignored -> new DailyAccumulator(fact.identity()))
                    .report(fact);
    }

    private void addQuantities(Map<String, DailyAccumulator> rows, List<QuantityFact> facts)
    {
        for (QuantityFact fact : facts)
            rows.computeIfAbsent(fact.identity().taskId(), ignored -> new DailyAccumulator(fact.identity()))
                    .quantity(fact);
    }

    private OrderDeliveryRow orderRow(List<OrderTaskFact> facts)
    {
        OrderTaskFact first = facts.get(0);
        Map<String, List<OrderTaskFact>> byLine = new LinkedHashMap<>();
        facts.forEach(value -> byLine.computeIfAbsent(value.orderLineId(), ignored -> new ArrayList<>()).add(value));
        List<OrderLineDeliveryRow> lines = byLine.values().stream().map(this::lineRow)
                .sorted(Comparator.comparingInt(OrderLineDeliveryRow::lineNo)).toList();
        boolean known = lines.stream().allMatch(value -> "KNOWN".equals(value.etaState()));
        boolean completed = lines.stream().allMatch(value ->
                value.releasedTerminalQty().compareTo(value.demandQty()) >= 0);
        Instant expected = known ? maxInstant(lines.stream().map(OrderLineDeliveryRow::expectedProductionAt).toList()) : null;
        Instant lowerBound = maxInstant(lines.stream().map(OrderLineDeliveryRow::knownLowerBoundAt).toList());
        List<DeliveryReason> reasons = lines.stream().flatMap(value -> value.reasons().stream()).distinct().toList();
        Instant promised = first.orderPromisedAt();
        Long delay = expected == null || promised == null ? null : Math.max(0, Duration.between(promised, expected).getSeconds());
        return new OrderDeliveryRow(first.orderId(), first.orderNo(), first.orderStatus(), promised,
                known ? "KNOWN" : "UNKNOWN", expected, lowerBound, delay, completed,
                "COMPLETED".equals(first.orderStatus()), reasons, lines);
    }

    private OrderLineDeliveryRow lineRow(List<OrderTaskFact> facts)
    {
        OrderTaskFact first = facts.get(0);
        List<OrderTaskFact> terminals = facts.stream().filter(OrderTaskFact::terminalTask).toList();
        BigDecimal released = terminals.stream().map(value -> zero(value.terminalReleasedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DeliveryReason> reasons = new ArrayList<>();
        for (OrderTaskFact fact : facts)
        {
            if (fact.hiddenScopeTaskCount() > 0)
                reasons.add(reason("SCOPE_PARTIAL", "ORDER_LINE", fact.orderLineId(),
                        "当前数据范围只覆盖该产品行的部分任务，不能声称整行 ETA"));
            if (fact.missingDuration()) reasons.add(reason("MISSING_DURATION", "TASK", fact.taskId(), "任务缺少可复算工时"));
            if (fact.missingResource()) reasons.add(reason("MISSING_RESOURCE", "TASK", fact.taskId(), "任务缺少工作中心或资源资格"));
            if (fact.unavailableMaterialCount() > 0)
                reasons.add(reason("MATERIAL_UNAVAILABLE", "TASK", fact.taskId(), "存在未就绪物料需求"));
            if (!Set.of("COMPLETED", "CANCELLED").contains(fact.taskStatus()) && fact.plannedEndAt() == null)
                reasons.add(reason("UNPLANNED_TASK", "TASK", fact.taskId(), "必需任务未进入当前正式计划"));
            if (fact.terminalTask() && fact.qualityHoldCount() > 0)
                reasons.add(reason("QUALITY_NOT_RELEASED", "TASK", fact.taskId(), "末端产出仍待检、隔离或拒收"));
        }
        if (terminals.isEmpty())
            reasons.add(reason("MISSING_TERMINAL_TASK", "ORDER_LINE", first.orderLineId(), "产品行没有可识别的末端任务"));
        boolean completed = released.compareTo(first.demandQty()) >= 0;
        if (!completed && terminals.stream().allMatch(value -> "COMPLETED".equals(value.taskStatus()))
                && released.compareTo(first.demandQty()) < 0)
            reasons.add(reason("TERMINAL_QUANTITY_SHORTFALL", "ORDER_LINE", first.orderLineId(), "末端合格放行量不足"));
        List<DeliveryReason> unique = reasons.stream().distinct().toList();
        boolean known = unique.isEmpty();
        Instant actualAt = maxInstant(terminals.stream().map(OrderTaskFact::terminalReleasedAt).toList());
        Instant plannedAt = maxInstant(terminals.stream().map(OrderTaskFact::plannedEndAt).toList());
        Instant expected = completed && actualAt != null ? actualAt : known ? plannedAt : null;
        Instant lowerBound = maxInstant(facts.stream().flatMap(value ->
                java.util.stream.Stream.of(value.plannedEndAt(), value.terminalReleasedAt())).toList());
        return new OrderLineDeliveryRow(first.orderLineId(), first.lineNo(), first.itemId(), first.itemCode(),
                first.itemName(), first.uomCode(), first.demandQty(), released, first.lineStatus(),
                first.linePromisedAt(), known ? "KNOWN" : "UNKNOWN", expected, lowerBound, unique);
    }

    private Peak peak(String skill, List<PersonFact> people,
            Map<String, List<AvailabilityFact>> availability, List<PlannedLaborFact> planned,
            Instant fromAt, Instant toAt)
    {
        List<PlannedLaborFact> demand = planned.stream().filter(value -> skill.equals(value.skillCode())).toList();
        Set<Instant> boundaries = new TreeSet<>();
        boundaries.add(fromAt);
        boundaries.add(toAt);
        demand.forEach(value -> { boundaries.add(max(value.startAt(), fromAt)); boundaries.add(min(value.endAt(), toAt)); });
        availability.values().stream().flatMap(List::stream).forEach(value -> {
            boundaries.add(max(value.startAt(), fromAt)); boundaries.add(min(value.endAt(), toAt));
        });
        people.stream().flatMap(value -> value.skillQualifications().stream())
                .filter(value -> skill.equals(value.skillCode())).forEach(value -> {
                    if (value.validFrom() != null && value.validFrom().isAfter(fromAt) && value.validFrom().isBefore(toAt))
                        boundaries.add(value.validFrom());
                    if (value.validTo() != null && value.validTo().isAfter(fromAt) && value.validTo().isBefore(toAt))
                        boundaries.add(value.validTo());
                });
        int bestShortage = 0;
        int bestRequired = 0;
        int bestQualified = 0;
        Instant bestAt = null;
        for (Instant at : boundaries)
        {
            if (!at.isBefore(toAt)) continue;
            // 同一段可有多个 PERSON 席位；峰值需求按分配席位计数，不能按 segment 去重。
            int required = (int) demand.stream().filter(value -> active(value.startAt(), value.endAt(), at))
                    .map(PlannedLaborFact::allocationId).distinct().count();
            int qualified = (int) people.stream().filter(value -> qualifiedAt(value, skill, at))
                    .filter(value -> availableAt(availability.getOrDefault(value.resourceId(), List.of()), at))
                    .count();
            int shortage = Math.max(0, required - qualified);
            if (shortage > bestShortage || (shortage == bestShortage && required > bestRequired))
            {
                bestShortage = shortage;
                bestRequired = required;
                bestQualified = qualified;
                bestAt = at;
            }
        }
        return new Peak(bestRequired, bestQualified, bestShortage, bestAt);
    }

    private boolean availableAt(List<AvailabilityFact> facts, Instant at)
    {
        boolean excluded = facts.stream().anyMatch(value -> !positive(value.windowType())
                && active(value.startAt(), value.endAt(), at));
        if (excluded) return false;
        return facts.stream().anyMatch(value -> positive(value.windowType())
                && value.capacityRatio().signum() > 0 && active(value.startAt(), value.endAt(), at));
    }

    private BigDecimal availabilityHours(List<AvailabilityFact> facts, Instant fromAt, Instant toAt)
    {
        Set<Instant> boundaries = new TreeSet<>();
        boundaries.add(fromAt);
        boundaries.add(toAt);
        for (AvailabilityFact fact : facts)
        {
            Instant start = max(fact.startAt(), fromAt);
            Instant end = min(fact.endAt(), toAt);
            if (start.isBefore(end)) { boundaries.add(start); boundaries.add(end); }
        }
        List<Instant> points = new ArrayList<>(boundaries);
        BigDecimal seconds = BigDecimal.ZERO;
        for (int i = 0; i + 1 < points.size(); i++)
        {
            Instant start = points.get(i);
            Instant end = points.get(i + 1);
            boolean excluded = facts.stream().anyMatch(value -> !positive(value.windowType())
                    && active(value.startAt(), value.endAt(), start));
            if (excluded) continue;
            BigDecimal ratio = facts.stream().filter(value -> positive(value.windowType())
                    && active(value.startAt(), value.endAt(), start)).map(AvailabilityFact::capacityRatio)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).min(BigDecimal.ONE);
            seconds = seconds.add(BigDecimal.valueOf(seconds(start, end)).multiply(ratio));
        }
        return hours(seconds);
    }

    private BigDecimal qualificationAvailabilityHours(PersonFact person, String skill,
            List<AvailabilityFact> facts, Instant fromAt, Instant toAt)
    {
        List<SkillQualification> qualifications = person.skillQualifications().stream()
                .filter(value -> skill.equals(value.skillCode())).toList();
        if (qualifications.isEmpty()) return BigDecimal.ZERO;
        Set<Instant> boundaries = new TreeSet<>();
        boundaries.add(fromAt);
        boundaries.add(toAt);
        for (AvailabilityFact fact : facts)
        {
            Instant start = max(fact.startAt(), fromAt);
            Instant end = min(fact.endAt(), toAt);
            if (start.isBefore(end)) { boundaries.add(start); boundaries.add(end); }
        }
        for (SkillQualification qualification : qualifications)
        {
            if (qualification.validFrom() != null && qualification.validFrom().isAfter(fromAt)
                    && qualification.validFrom().isBefore(toAt)) boundaries.add(qualification.validFrom());
            if (qualification.validTo() != null && qualification.validTo().isAfter(fromAt)
                    && qualification.validTo().isBefore(toAt)) boundaries.add(qualification.validTo());
        }
        List<Instant> points = new ArrayList<>(boundaries);
        BigDecimal seconds = BigDecimal.ZERO;
        for (int i = 0; i + 1 < points.size(); i++)
        {
            Instant start = points.get(i);
            Instant end = points.get(i + 1);
            if (!qualifiedAt(person, skill, start)) continue;
            boolean excluded = facts.stream().anyMatch(value -> !positive(value.windowType())
                    && active(value.startAt(), value.endAt(), start));
            if (excluded) continue;
            BigDecimal ratio = facts.stream().filter(value -> positive(value.windowType())
                    && active(value.startAt(), value.endAt(), start)).map(AvailabilityFact::capacityRatio)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).min(BigDecimal.ONE);
            seconds = seconds.add(BigDecimal.valueOf(seconds(start, end)).multiply(ratio));
        }
        return hours(seconds);
    }

    private boolean qualifiedAt(PersonFact person, String skill, Instant at)
    {
        return person.skillQualifications().stream().filter(value -> skill.equals(value.skillCode()))
                .anyMatch(value -> (value.validFrom() == null || !at.isBefore(value.validFrom()))
                        && (value.validTo() == null || at.isBefore(value.validTo())));
    }

    private Map<String, List<AvailabilityFact>> groupAvailability(List<AvailabilityFact> facts)
    {
        Map<String, List<AvailabilityFact>> result = new LinkedHashMap<>();
        facts.forEach(value -> result.computeIfAbsent(value.resourceId(), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    private ReportMetadata metadata(Instant fromAt, Instant toAt, PlanReference baseline,
            PlanReference current, Instant cutoff)
    {
        return new ReportMetadata(siteCode, zoneId.getId(), fromAt, toAt, baseline == null ? null : baseline.id(),
                current == null ? null : current.id(), cutoff, clock.instant());
    }

    private BigDecimal ratio(BigDecimal memberQty, BigDecimal jobQty)
    {
        if (memberQty == null || jobQty == null || jobQty.signum() <= 0) return BigDecimal.ONE;
        return memberQty.divide(jobQty, 12, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }

    private static BigDecimal hours(long seconds) { return hours(BigDecimal.valueOf(seconds)); }
    private static BigDecimal hours(BigDecimal seconds)
    {
        return seconds.divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }
    private static long seconds(Instant start, Instant end)
    {
        return start == null || end == null || !start.isBefore(end) ? 0 : Duration.between(start, end).getSeconds();
    }
    private static long mergedSeconds(List<Interval> source)
    {
        List<Interval> intervals = source.stream().filter(value -> value.start().isBefore(value.end()))
                .sorted(Comparator.comparing(Interval::start)).toList();
        if (intervals.isEmpty()) return 0;
        long total = 0;
        Instant start = intervals.get(0).start();
        Instant end = intervals.get(0).end();
        for (int i = 1; i < intervals.size(); i++)
        {
            Interval next = intervals.get(i);
            if (!next.start().isAfter(end)) end = max(end, next.end());
            else { total += seconds(start, end); start = next.start(); end = next.end(); }
        }
        return total + seconds(start, end);
    }
    private static boolean positive(String type) { return "AVAILABLE".equals(type) || "OVERTIME".equals(type); }
    private static boolean active(Instant start, Instant end, Instant at)
    {
        return !at.isBefore(start) && at.isBefore(end);
    }
    private static Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
    private static Instant maxInstant(List<Instant> values)
    {
        return values.stream().filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullSafe(String value) { return value == null ? "" : value; }
    private static String required(String value, String message)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
    private static DeliveryReason reason(String code, String type, String id, String detail)
    {
        return new DeliveryReason(code, type, id, detail);
    }
    private static void invalid(String message) { throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message); }

    private record Interval(Instant start, Instant end) { }
    private record Peak(int required, int qualified, int shortage, Instant at) { }

    private static final class DailyAccumulator
    {
        private final TaskIdentity identity;
        private final Set<String> baselineSegments = new LinkedHashSet<>();
        private final Set<String> currentSegments = new LinkedHashSet<>();
        private final Set<String> actualOccupancies = new LinkedHashSet<>();
        private final Set<String> reportIds = new LinkedHashSet<>();
        private final Set<String> quantityIds = new LinkedHashSet<>();
        private final Set<String> plannedPeople = new TreeSet<>();
        private final Set<String> plannedMachines = new TreeSet<>();
        private final Set<String> actualPeople = new TreeSet<>();
        private final Set<String> actualMachines = new TreeSet<>();
        private Instant baselineStart;
        private Instant baselineEnd;
        private Instant currentStart;
        private Instant currentEnd;
        private BigDecimal baselineQty = BigDecimal.ZERO;
        private BigDecimal currentQty = BigDecimal.ZERO;
        private BigDecimal baselinePersonSeconds = BigDecimal.ZERO;
        private BigDecimal baselineMachineSeconds = BigDecimal.ZERO;
        private BigDecimal currentPersonSeconds = BigDecimal.ZERO;
        private BigDecimal currentMachineSeconds = BigDecimal.ZERO;
        private BigDecimal actualPersonSeconds = BigDecimal.ZERO;
        private BigDecimal actualMachineSeconds = BigDecimal.ZERO;
        private BigDecimal processed = BigDecimal.ZERO;
        private BigDecimal rejected = BigDecimal.ZERO;
        private BigDecimal scrap = BigDecimal.ZERO;
        private BigDecimal transferred = BigDecimal.ZERO;
        private BigDecimal good = BigDecimal.ZERO;

        private DailyAccumulator(TaskIdentity identity) { this.identity = identity; }

        private void plan(PlanSegmentFact fact, boolean baseline, BigDecimal ratio,
                Instant fromAt, Instant toAt)
        {
            Set<String> seen = baseline ? baselineSegments : currentSegments;
            if (seen.add(fact.planSegmentId()))
            {
                if (baseline)
                {
                    baselineStart = baselineStart == null ? fact.startAt() : min(baselineStart, fact.startAt());
                    baselineEnd = baselineEnd == null ? fact.endAt() : max(baselineEnd, fact.endAt());
                    if (fact.releaseAt() != null && !fact.releaseAt().isBefore(fromAt) && fact.releaseAt().isBefore(toAt))
                        baselineQty = baselineQty.add(zero(fact.releaseQty()).multiply(ratio));
                }
                else
                {
                    currentStart = currentStart == null ? fact.startAt() : min(currentStart, fact.startAt());
                    currentEnd = currentEnd == null ? fact.endAt() : max(currentEnd, fact.endAt());
                    if (fact.releaseAt() != null && !fact.releaseAt().isBefore(fromAt) && fact.releaseAt().isBefore(toAt))
                        currentQty = currentQty.add(zero(fact.releaseQty()).multiply(ratio));
                }
            }
            if (fact.allocationId() == null) return;
            BigDecimal duration = BigDecimal.valueOf(seconds(max(fact.startAt(), fromAt), min(fact.endAt(), toAt)))
                    .multiply(ratio);
            boolean person = "PERSON".equals(fact.allocationRole());
            if (baseline)
            {
                if (person) baselinePersonSeconds = baselinePersonSeconds.add(duration);
                else if ("MACHINE".equals(fact.allocationRole())) baselineMachineSeconds = baselineMachineSeconds.add(duration);
            }
            else
            {
                if (person) { currentPersonSeconds = currentPersonSeconds.add(duration); plannedPeople.add(fact.resourceId()); }
                else if ("MACHINE".equals(fact.allocationRole())) { currentMachineSeconds = currentMachineSeconds.add(duration); plannedMachines.add(fact.resourceId()); }
            }
        }

        private void actual(ActualOccupancyFact fact, BigDecimal ratio, Instant fromAt, Instant toAt, Instant cutoff)
        {
            String key = fact.occupancyId() + "\u0000" + identity.taskId();
            if (!actualOccupancies.add(key)) return;
            Instant end = fact.endAt() == null ? cutoff : fact.endAt();
            BigDecimal duration = BigDecimal.valueOf(seconds(max(fact.startAt(), fromAt), min(end, toAt)))
                    .multiply(ratio);
            if ("PERSON".equals(fact.resourceType()))
            {
                actualPersonSeconds = actualPersonSeconds.add(duration);
                actualPeople.add(fact.resourceId());
            }
            else if ("MACHINE".equals(fact.resourceType()))
            {
                actualMachineSeconds = actualMachineSeconds.add(duration);
                actualMachines.add(fact.resourceId());
            }
        }

        private void report(ProductionReportFact fact)
        {
            if (!reportIds.add(fact.reportId())) return;
            processed = processed.add(zero(fact.processedQty()));
            rejected = rejected.add(zero(fact.rejectedQty()));
            // 报工中的 scrap/transferred 是现场分类，最终桶变化只认 M29，避免同一事实重复统计。
        }

        private void quantity(QuantityFact fact)
        {
            if (!quantityIds.add(fact.eventId())) return;
            int sign = "REVERSE".equals(fact.eventType()) ? -1 : 1;
            String effective = "REVERSE".equals(fact.eventType()) ? fact.originalEventType() : fact.eventType();
            if ("QUALITY_RELEASE".equals(effective)) good = good.add(fact.quantity().multiply(BigDecimal.valueOf(sign)));
            if ("SCRAP".equals(effective)) scrap = scrap.add(fact.quantity().multiply(BigDecimal.valueOf(sign)));
            if ("TRANSFER".equals(effective)) transferred = transferred.add(fact.quantity().multiply(BigDecimal.valueOf(sign)));
        }

        private DailyTaskRow row(boolean baselineAvailable)
        {
            BigDecimal achievement = baselineQty.signum() == 0 ? null
                    : good.divide(baselineQty, 6, RoundingMode.HALF_UP);
            BigDecimal carryover = currentQty.subtract(good).max(BigDecimal.ZERO);
            boolean delayed = identity.promisedAt() != null && currentEnd != null
                    && currentEnd.isAfter(identity.promisedAt());
            List<String> reasons = new ArrayList<>();
            if (!baselineAvailable) reasons.add("DAILY_BASELINE_MISSING");
            if (currentSegments.isEmpty()) reasons.add("NOT_IN_CURRENT_PLAN");
            if (delayed) reasons.add("PROMISE_AT_RISK");
            return new DailyTaskRow(identity.workshopId(), identity.workshopCode(), identity.workshopName(),
                    identity.workCenterId(), identity.workCenterCode(), identity.workCenterName(),
                    identity.operationSpecId(), identity.operationCode(), identity.operationName(),
                    identity.orderId(), identity.orderNo(), identity.orderLineId(), identity.lineNo(),
                    identity.itemId(), identity.itemCode(), identity.itemName(), identity.productionLotId(),
                    identity.lotNo(), identity.taskId(), identity.taskCode(), identity.taskName(),
                    identity.taskStatus(), identity.uomCode(), identity.promisedAt(),
                    baselineStart, baselineEnd, baselineQty, hours(baselinePersonSeconds), hours(baselineMachineSeconds),
                    currentStart, currentEnd, currentQty, hours(currentPersonSeconds), hours(currentMachineSeconds),
                    processed, good.max(BigDecimal.ZERO), rejected, scrap.max(BigDecimal.ZERO),
                    transferred.max(BigDecimal.ZERO), hours(actualPersonSeconds), hours(actualMachineSeconds),
                    achievement, carryover, delayed, List.copyOf(plannedPeople), List.copyOf(plannedMachines),
                    List.copyOf(actualPeople), List.copyOf(actualMachines), reasons);
        }
    }
}
