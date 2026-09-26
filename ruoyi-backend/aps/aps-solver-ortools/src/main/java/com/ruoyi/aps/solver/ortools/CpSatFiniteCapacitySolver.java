package com.ruoyi.aps.solver.ortools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.IntervalVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.MaterialReleaseMath;
import com.ruoyi.aps.solver.contract.Problem;
import com.ruoyi.aps.solver.contract.QuantityReleaseMath;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import com.ruoyi.aps.solver.contract.SolverResult;
import com.ruoyi.aps.solver.contract.TimeAxis;
import com.ruoyi.aps.solver.contract.ValidationResult;
import com.ruoyi.aps.validator.IndependentConstraintValidator;

/**
 * IMP-07 有限产能 CP-SAT 适配器。
 *
 * <p>执行普通任务、固定共享批、阶段顺序、完成依赖、连续数量释放和已批准的受控可中断分段；
 * 尚未建模的组合及基础版本重排继续显式阻断。任何候选都要经过独立校验器复验。</p>
 */
public final class CpSatFiniteCapacitySolver
{
    public static final String MODEL_VERSION = "aps-cpsat-v1";
    public static final String SOLVER_VERSION = "ortools-9.15.6755";
    private static final long CAPACITY_SCALE = 1_000_000L;
    private static final int LARGE_INDEPENDENT_HINT_THRESHOLD = 1_000;
    private final IndependentConstraintValidator validator = new IndependentConstraintValidator();
    private final SolverInputCodec codec = new SolverInputCodec();

    public SolverResult solve(SolverInput input, Instant generatedAt)
    {
        return solve(input, generatedAt, () -> false);
    }

    public SolverResult solve(SolverInput input, Instant generatedAt, BooleanSupplier cancellationRequested)
    {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        ValidationResult readiness = validator.validateInput(input, null, generatedAt);
        if (readiness.validationStatus() == ValidationResult.Status.FAIL)
            return rejected(input, generatedAt, readiness.problems());
        if (cancellationRequested.getAsBoolean()) return cancelled(input, generatedAt);
        List<SolverInput.Dependency> unsupported = input.dependencies().stream()
                .filter(value -> value.relationType() == SolverInput.RelationType.SAME_START).toList();
        if (!unsupported.isEmpty())
            return rejected(input, generatedAt, unsupported.stream().map(value -> problem(
                    Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED, "DEPENDENCY", value.dependencyId(),
                    "求解器暂不执行同步依赖", "SAME_START 继续阻断，不能降级成完成或数量依赖")).toList());
        Set<String> interruptibleTasks = input.tasks().stream()
                .filter(task -> task.phases().stream().anyMatch(SolverInput.Phase::interruptible))
                .map(SolverInput.Task::taskId).collect(java.util.stream.Collectors.toSet());
        if (input.sharedBatchCandidates().stream().flatMap(batch -> batch.members().stream())
                .anyMatch(member -> interruptibleTasks.contains(member.taskId())))
            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                    "PLAN_VERSION", input.planVersionId(), "共享批暂不与可中断运行组合",
                    "固定共享周期必须保持一个连续设备区间；若业务允许中断共享炉次，需要单独批准批语义")));
        boolean segmentedQuantityFlow = input.dependencies().stream()
                .anyMatch(value -> value.relationType() == SolverInput.RelationType.QUANTITY
                        && interruptibleTasks.contains(value.predecessorTaskId()))
                || input.materialDemands().stream().anyMatch(value -> value.sourceTaskId() != null
                        && interruptibleTasks.contains(value.sourceTaskId()));
        if (segmentedQuantityFlow)
            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                    "PLAN_VERSION", input.planVersionId(), "可中断阶段暂不同时承担连续数量释放",
                    "物理分段与转移批释放的交叉语义尚未批准，不能按连续运行近似")));
        Map<String, SolverInput.Resource> inputResourceById = input.resources().stream().collect(
                java.util.stream.Collectors.toMap(SolverInput.Resource::resourceId, value -> value));
        boolean unsupportedCapacityHold = input.tasks().stream().flatMap(task -> task.phases().stream())
                .filter(SolverInput.Phase::interruptible).flatMap(phase -> phase.requirements().stream())
                .filter(SolverInput.ResourceRequirement::holdOnPause)
                .flatMap(requirement -> requirement.candidateResourceIds().stream()).map(inputResourceById::get)
                .filter(Objects::nonNull).anyMatch(resource -> !resource.exclusive());
        if (unsupportedCapacityHold)
            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                    "PLAN_VERSION", input.planVersionId(), "容量资源暂停保留尚未建模",
                    "本阶段只执行独占人员、设备、工位或工装的暂停保留；容量池不能被静默按独占近似")));
        Map<String, SolverInput.SharedBatchCandidate> batchByTask = new HashMap<>();
        for (SolverInput.SharedBatchCandidate batch : input.sharedBatchCandidates())
            for (SolverInput.SharedBatchMember member : batch.members())
                if (batchByTask.putIfAbsent(member.taskId(), batch) != null)
                    return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                            "TASK", member.taskId(), "共享批候选存在重叠成员",
                            "当前模型只执行成员归属唯一的固定候选；互斥候选选择需由后续模型显式表达")));

        Map<String, SolverInput.Task> inputTasks = input.tasks().stream().collect(
                java.util.stream.Collectors.toMap(SolverInput.Task::taskId, value -> value));
        Loader.loadNativeLibraries();
        long startedNanos = System.nanoTime();
        TimeAxis axis = new TimeAxis(input.horizon().startAt(), input.horizon().endAt(),
                input.horizon().timeUnitSeconds());
        CpModel model = new CpModel();
        Map<String, SolverInput.Resource> resources = input.resources().stream()
                .collect(java.util.stream.Collectors.toMap(SolverInput.Resource::resourceId, value -> value));
        Map<String, List<SolverInput.AvailabilityWindow>> windows = input.availabilityWindows().stream()
                .collect(java.util.stream.Collectors.groupingBy(SolverInput.AvailabilityWindow::resourceId));
        Map<String, List<IntervalVar>> exclusiveIntervals = new HashMap<>();
        Map<WindowKey, List<DemandInterval>> capacityIntervals = new HashMap<>();
        Map<String, TaskVars> tasks = new LinkedHashMap<>();
        Map<String, TaskVars> carryVarsByRun = new LinkedHashMap<>();
        Map<String, TaskVars> carryVarsByTask = new HashMap<>();
        CarryProjection carry;
        try { carry = carryProjection(input, inputTasks); }
        catch (IllegalArgumentException exception)
        {
            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CONTRACT_VALIDATION_FAILED,
                    "PLAN_VERSION", input.planVersionId(), "活动 run 的 carry 数量无法守恒", exception.getMessage())));
        }
        Map<String, BigDecimal> requiredRelease = QuantityReleaseMath.requiredReleaseByDependency(
                input.dependencies(), inputTasks);
        MaterialReleaseMath.ReleasePlan materialRelease = MaterialReleaseMath.plan(input);
        int horizon = axis.horizonUnits();

        for (SolverInput.Task sourceTask : input.tasks().stream()
                .filter(value -> value.planningClass() == SolverInput.PlanningClass.MANDATORY_DETAIL)
                .sorted(Comparator.comparing(SolverInput.Task::taskId)).toList())
        {
            SolverInput.SharedBatchCandidate shared = batchByTask.get(sourceTask.taskId());
            BigDecimal futureQuantity = decimal(sourceTask.quantity()).subtract(
                    carry.quantityByTask().getOrDefault(sourceTask.taskId(), BigDecimal.ZERO));
            if (shared != null && shared.members().stream().anyMatch(member ->
                    carry.quantityByTask().getOrDefault(member.taskId(), BigDecimal.ZERO).signum() > 0))
            {
                boolean allCarried = shared.members().stream().allMatch(member -> decimal(member.quantity())
                        .subtract(carry.quantityByTask().getOrDefault(member.taskId(), BigDecimal.ZERO)).signum() == 0);
                if (!allCarried)
                    return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                            "TASK", sourceTask.taskId(), "共享批 carry 与未来拆分不能混合",
                            "共享 run 必须完整携带全部剩余成员；部分共享派工需要先补充成员级分配事实")));
                continue;
            }
            if (futureQuantity.signum() == 0) continue;
            List<SolverInput.Task> memberTasks = shared == null ? List.of(sourceTask) : shared.members().stream()
                    .map(SolverInput.SharedBatchMember::taskId).map(inputTasks::get)
                    .sorted(Comparator.comparing(SolverInput.Task::taskId)).toList();
            if (shared != null && !sourceTask.taskId().equals(memberTasks.get(0).taskId())) continue;
            SolverInput.Task task = shared == null ? withQuantity(sourceTask, futureQuantity)
                    : combinedTask(shared, memberTasks);
            List<PhaseVars> phases = new ArrayList<>();
            int earliest = axis.ceilOffset(task.earliestStartAt());
            for (SolverInput.Phase phase : task.phases().stream()
                    .sorted(Comparator.comparingInt(SolverInput.Phase::sequenceNo)).toList())
            {
                int duration = shared != null && phase.phaseType() == SolverInput.PhaseType.RUN
                        ? axis.ceilDurationSeconds(shared.cycleDurationSeconds()) : durationUnits(task, phase, axis);
                if (phase.interruptible())
                {
                    int minimum = axis.ceilDurationSeconds(phase.minSegmentSeconds());
                    int resume = axis.ceilDurationSeconds(phase.resumeSetupSeconds());
                    List<SegmentVars> segmentVars = new ArrayList<>();
                    LinearExprBuilder productive = LinearExpr.newBuilder();
                    for (int segmentNo = 0; segmentNo < phase.maxSegments(); segmentNo++)
                    {
                        BoolVar present = model.newBoolVar(name("present", phase.phaseId(), Integer.toString(segmentNo + 1)));
                        IntVar segmentStart = model.newIntVar(earliest, horizon,
                                name("start", phase.phaseId(), Integer.toString(segmentNo + 1)));
                        IntVar segmentEnd = model.newIntVar(earliest, horizon,
                                name("end", phase.phaseId(), Integer.toString(segmentNo + 1)));
                        IntVar segmentDuration = model.newIntVar(0, duration + resume,
                                name("duration", phase.phaseId(), Integer.toString(segmentNo + 1)));
                        if (segmentNo == 0) model.addEquality(present, 1);
                        else
                        {
                            model.addImplication(present, segmentVars.get(segmentNo - 1).present());
                            model.addLessOrEqual(segmentVars.get(segmentNo - 1).end(), segmentStart)
                                    .onlyEnforceIf(present);
                        }
                        model.addGreaterOrEqual(segmentDuration, minimum + (segmentNo == 0 ? 0 : resume))
                                .onlyEnforceIf(present);
                        model.addEquality(segmentDuration, 0).onlyEnforceIf(present.not());
                        model.addEquality(segmentStart, earliest).onlyEnforceIf(present.not());
                        model.addEquality(segmentEnd, earliest).onlyEnforceIf(present.not());
                        model.newOptionalIntervalVar(segmentStart, segmentDuration, segmentEnd, present,
                                name("work", phase.phaseId(), Integer.toString(segmentNo + 1)));
                        productive.add(segmentDuration);
                        if (segmentNo > 0 && resume > 0) productive.addTerm(present, -resume);
                        segmentVars.add(new SegmentVars(segmentStart, segmentEnd, segmentDuration, present, segmentNo + 1,
                                segmentNo == 0 ? 0 : resume, new ArrayList<>()));
                    }
                    model.addEquality(productive, duration);
                    IntVar phaseEnd = model.newIntVar(earliest, horizon, name("end", task.taskId(), phase.phaseId()));
                    model.addMaxEquality(phaseEnd, segmentVars.stream().map(SegmentVars::end).toArray(IntVar[]::new));
                    IntVar phaseStart = segmentVars.get(0).start();
                    for (SolverInput.ResourceRequirement requirement : phase.requirements())
                    {
                        Map<String, List<BoolVar>> duplicateSeatChoices = new HashMap<>();
                        for (int seat = 1; seat <= requirement.seatCount(); seat++)
                        {
                            Map<String, BoolVar> masterByResource = new LinkedHashMap<>();
                            if (phase.segmentResourcePolicy() == SolverInput.SegmentResourcePolicy.SAME_RESOURCES)
                            {
                                for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                                    if (resources.containsKey(resourceId)) masterByResource.put(resourceId,
                                            model.newBoolVar(name("master", phase.phaseId(), requirement.requirementId(),
                                                    Integer.toString(seat), resourceId)));
                                if (masterByResource.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                        Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT", requirement.requirementId(),
                                        "可中断阶段没有候选资源", "资源连续性规则无法选择稳定资源")));
                                model.addExactlyOne(masterByResource.values().stream()
                                        .map(value -> (Literal) value).toList());
                            }
                            for (SegmentVars segment : segmentVars)
                            {
                                List<BoolVar> seatChoices = new ArrayList<>();
                                for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                                {
                                    SolverInput.Resource resource = resources.get(resourceId);
                                    if (resource == null) continue;
                                    for (SolverInput.AvailabilityWindow window : windows.getOrDefault(resourceId, List.of()))
                                    {
                                        if (decimal(window.capacity()).compareTo(decimal(requirement.capacityDemand())) < 0) continue;
                                        int windowStart = axis.ceilOffset(window.startAt());
                                        int windowEnd = axis.floorOffset(window.endAt());
                                        if (windowEnd - windowStart < minimum + (segment.segmentNo() == 1 ? 0 : resume)) continue;
                                        BoolVar selected = model.newBoolVar(name("choose", phase.phaseId(),
                                                requirement.requirementId(), Integer.toString(segment.segmentNo()),
                                                Integer.toString(seat), resourceId, window.availabilityId()));
                                        model.addImplication(selected, segment.present());
                                        model.addGreaterOrEqual(segment.start(), windowStart).onlyEnforceIf(selected);
                                        model.addLessOrEqual(segment.end(), windowEnd).onlyEnforceIf(selected);
                                        if (!masterByResource.isEmpty())
                                            model.addImplication(selected, masterByResource.get(resourceId));
                                        IntervalVar interval = model.newOptionalIntervalVar(segment.start(),
                                                segment.duration(), segment.end(), selected, name("use", phase.phaseId(),
                                                        requirement.requirementId(), Integer.toString(segment.segmentNo()),
                                                        Integer.toString(seat), resourceId, window.availabilityId()));
                                        Choice choice = new Choice(requirement, resource, window, seat, selected, interval);
                                        segment.choices().add(choice);
                                        seatChoices.add(selected);
                                        duplicateSeatChoices.computeIfAbsent(segment.segmentNo() + ":" + resourceId,
                                                ignored -> new ArrayList<>()).add(selected);
                                        if (!requirement.holdOnPause())
                                        {
                                            if (resource.exclusive())
                                                exclusiveIntervals.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(interval);
                                            else
                                                capacityIntervals.computeIfAbsent(new WindowKey(resourceId, window.availabilityId()),
                                                        ignored -> new ArrayList<>()).add(new DemandInterval(interval,
                                                                scaled(requirement.capacityDemand())));
                                        }
                                    }
                                }
                                if (seatChoices.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                        Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT", requirement.requirementId(),
                                        "候选资源没有可承载分段的净窗口", "最小分段、恢复准备、容量与净日历组合后没有可选区间")));
                                model.addEquality(LinearExpr.sum(seatChoices.toArray(BoolVar[]::new)), segment.present());
                            }
                            if (requirement.holdOnPause())
                                for (Map.Entry<String, BoolVar> entry : masterByResource.entrySet())
                                {
                                    IntVar holdDuration = model.newIntVar(0, horizon,
                                            name("hold_duration", phase.phaseId(), requirement.requirementId(),
                                                    Integer.toString(seat), entry.getKey()));
                                    IntervalVar hold = model.newOptionalIntervalVar(phaseStart, holdDuration, phaseEnd,
                                            entry.getValue(), name("hold", phase.phaseId(), requirement.requirementId(),
                                                    Integer.toString(seat), entry.getKey()));
                                    exclusiveIntervals.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(hold);
                                }
                        }
                        duplicateSeatChoices.values().forEach(values -> model.addAtMostOne(
                                values.stream().map(value -> (Literal) value).toList()));
                    }
                    phases.add(new PhaseVars(task, phase, phaseStart, phaseEnd, duration, segmentVars));
                    continue;
                }
                IntVar start = model.newIntVar(earliest, horizon, name("start", task.taskId(), phase.phaseId()));
                IntVar end = model.newIntVar(earliest, horizon, name("end", task.taskId(), phase.phaseId()));
                model.addEquality(end, LinearExpr.newBuilder().add(start).add(duration));
                List<Choice> choices = new ArrayList<>();
                for (SolverInput.ResourceRequirement requirement : phase.requirements())
                {
                    Map<String, List<Literal>> byResourceForRequirement = new HashMap<>();
                    for (int seat = 1; seat <= requirement.seatCount(); seat++)
                    {
                        List<Literal> seatChoices = new ArrayList<>();
                        for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                        {
                            SolverInput.Resource resource = resources.get(resourceId);
                            if (resource == null) continue;
                            for (SolverInput.AvailabilityWindow window : windows.getOrDefault(resourceId, List.of()))
                            {
                                if (decimal(window.capacity()).compareTo(decimal(requirement.capacityDemand())) < 0) continue;
                                int windowStart = axis.ceilOffset(window.startAt());
                                int windowEnd = axis.floorOffset(window.endAt());
                                if (windowEnd - windowStart < duration) continue;
                                BoolVar selected = model.newBoolVar(name("choose", phase.phaseId(), requirement.requirementId(),
                                        Integer.toString(seat), resourceId, window.availabilityId()));
                                model.addGreaterOrEqual(start, windowStart).onlyEnforceIf(selected);
                                model.addLessOrEqual(end, windowEnd).onlyEnforceIf(selected);
                                IntervalVar interval = model.newOptionalFixedSizeIntervalVar(start, duration, selected,
                                        name("use", phase.phaseId(), requirement.requirementId(), Integer.toString(seat),
                                                resourceId, window.availabilityId()));
                                Choice choice = new Choice(requirement, resource, window, seat, selected, interval);
                                choices.add(choice);
                                seatChoices.add(selected);
                                byResourceForRequirement.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(selected);
                                if (resource.exclusive())
                                    exclusiveIntervals.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(interval);
                                else
                                    capacityIntervals.computeIfAbsent(new WindowKey(resourceId, window.availabilityId()),
                                            ignored -> new ArrayList<>()).add(new DemandInterval(interval,
                                                    scaled(requirement.capacityDemand())));
                            }
                        }
                        if (seatChoices.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT", requirement.requirementId(),
                                "候选资源没有可承载阶段的净窗口", "资源、容量与净日历组合后没有任何可选区间")));
                        model.addExactlyOne(seatChoices);
                    }
                    byResourceForRequirement.values().forEach(model::addAtMostOne);
                }
                phases.add(new PhaseVars(task, phase, start, end, duration,
                        List.of(new SegmentVars(start, end, null, null, 1, 0, choices))));
            }
            for (int index = 1; index < phases.size(); index++)
                model.addLessOrEqual(phases.get(index - 1).end(), phases.get(index).start());
            TaskVars taskVars = new TaskVars(task, phases, memberTasks, shared);
            memberTasks.forEach(member -> tasks.put(member.taskId(), taskVars));
        }

        for (CarryRun run : carry.runs())
        {
            if (run.laterPhases().isEmpty()) continue;
            List<SolverInput.Task> members = run.members().stream()
                    .map(SolverInput.ActualOccupancyMember::taskId).map(inputTasks::get)
                    .sorted(Comparator.comparing(SolverInput.Task::taskId)).toList();
            SolverInput.Task first = members.get(0);
            BigDecimal quantity = run.members().stream().map(value -> decimal(value.remainingQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Instant promised = members.stream().map(SolverInput.Task::promisedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            SolverInput.Task task = new SolverInput.Task(stableId("carry-task", run.executionRunId()),
                    first.orderLineId(), first.operationSpecId(), first.workCenterId(),
                    SolverInput.PlanningClass.MANDATORY_DETAIL, decimal(quantity), first.uomCode(), run.endAt(),
                    promised, run.laterPhases());
            List<PhaseVars> phases = new ArrayList<>();
            int earliest = axis.ceilOffset(run.endAt());
            for (SolverInput.Phase phase : run.laterPhases())
            {
                int duration = durationUnits(task, phase, axis);
                if (phase.interruptible())
                {
                    int minimum = axis.ceilDurationSeconds(phase.minSegmentSeconds());
                    int resume = axis.ceilDurationSeconds(phase.resumeSetupSeconds());
                    List<SegmentVars> segmentVars = new ArrayList<>();
                    LinearExprBuilder productive = LinearExpr.newBuilder();
                    for (int localSegmentNo = 0; localSegmentNo < phase.maxSegments(); localSegmentNo++)
                    {
                        BoolVar present = model.newBoolVar(name("carry_present", run.executionRunId(),
                                phase.phaseId(), Integer.toString(localSegmentNo + 1)));
                        IntVar segmentStart = model.newIntVar(earliest, horizon, name("carry_start",
                                run.executionRunId(), phase.phaseId(), Integer.toString(localSegmentNo + 1)));
                        IntVar segmentEnd = model.newIntVar(earliest, horizon, name("carry_end",
                                run.executionRunId(), phase.phaseId(), Integer.toString(localSegmentNo + 1)));
                        IntVar segmentDuration = model.newIntVar(0, duration + resume, name("carry_duration",
                                run.executionRunId(), phase.phaseId(), Integer.toString(localSegmentNo + 1)));
                        if (localSegmentNo == 0) model.addEquality(present, 1);
                        else
                        {
                            model.addImplication(present, segmentVars.get(localSegmentNo - 1).present());
                            model.addLessOrEqual(segmentVars.get(localSegmentNo - 1).end(), segmentStart)
                                    .onlyEnforceIf(present);
                        }
                        model.addGreaterOrEqual(segmentDuration,
                                minimum + (localSegmentNo == 0 ? 0 : resume)).onlyEnforceIf(present);
                        model.addEquality(segmentDuration, 0).onlyEnforceIf(present.not());
                        model.addEquality(segmentStart, earliest).onlyEnforceIf(present.not());
                        model.addEquality(segmentEnd, earliest).onlyEnforceIf(present.not());
                        model.newOptionalIntervalVar(segmentStart, segmentDuration, segmentEnd, present,
                                name("carry_work", run.executionRunId(), phase.phaseId(),
                                        Integer.toString(localSegmentNo + 1)));
                        productive.add(segmentDuration);
                        if (localSegmentNo > 0 && resume > 0) productive.addTerm(present, -resume);
                        segmentVars.add(new SegmentVars(segmentStart, segmentEnd, segmentDuration, present,
                                localSegmentNo + 1, localSegmentNo == 0 ? 0 : resume, new ArrayList<>()));
                    }
                    model.addEquality(productive, duration);
                    IntVar phaseEnd = model.newIntVar(earliest, horizon,
                            name("carry_phase_end", run.executionRunId(), phase.phaseId()));
                    model.addMaxEquality(phaseEnd,
                            segmentVars.stream().map(SegmentVars::end).toArray(IntVar[]::new));
                    IntVar phaseStart = segmentVars.get(0).start();
                    for (SolverInput.ResourceRequirement requirement : phase.requirements())
                    {
                        Map<String, List<BoolVar>> duplicateSeatChoices = new HashMap<>();
                        for (int seat = 1; seat <= requirement.seatCount(); seat++)
                        {
                            Map<String, BoolVar> masterByResource = new LinkedHashMap<>();
                            if (phase.segmentResourcePolicy() == SolverInput.SegmentResourcePolicy.SAME_RESOURCES)
                            {
                                for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                                    if (resources.containsKey(resourceId)) masterByResource.put(resourceId,
                                            model.newBoolVar(name("carry_master", run.executionRunId(), phase.phaseId(),
                                                    requirement.requirementId(), Integer.toString(seat), resourceId)));
                                if (masterByResource.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                        Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT",
                                        requirement.requirementId(), "carry 可中断阶段没有候选资源",
                                        "资源连续性规则无法选择稳定资源")));
                                model.addExactlyOne(masterByResource.values().stream()
                                        .map(value -> (Literal) value).toList());
                            }
                            for (SegmentVars segment : segmentVars)
                            {
                                List<BoolVar> seatChoices = new ArrayList<>();
                                for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                                {
                                    SolverInput.Resource resource = resources.get(resourceId);
                                    if (resource == null) continue;
                                    for (SolverInput.AvailabilityWindow window : windows.getOrDefault(resourceId,
                                            List.of()))
                                    {
                                        if (decimal(window.capacity()).compareTo(
                                                decimal(requirement.capacityDemand())) < 0) continue;
                                        int windowStart = axis.ceilOffset(window.startAt());
                                        int windowEnd = axis.floorOffset(window.endAt());
                                        if (windowEnd - windowStart < minimum
                                                + (segment.segmentNo() == 1 ? 0 : resume)) continue;
                                        BoolVar selected = model.newBoolVar(name("carry_choose",
                                                run.executionRunId(), phase.phaseId(), requirement.requirementId(),
                                                Integer.toString(segment.segmentNo()), Integer.toString(seat),
                                                resourceId, window.availabilityId()));
                                        model.addImplication(selected, segment.present());
                                        model.addGreaterOrEqual(segment.start(), windowStart).onlyEnforceIf(selected);
                                        model.addLessOrEqual(segment.end(), windowEnd).onlyEnforceIf(selected);
                                        if (!masterByResource.isEmpty())
                                            model.addImplication(selected, masterByResource.get(resourceId));
                                        IntervalVar interval = model.newOptionalIntervalVar(segment.start(),
                                                segment.duration(), segment.end(), selected, name("carry_use",
                                                        run.executionRunId(), phase.phaseId(),
                                                        requirement.requirementId(),
                                                        Integer.toString(segment.segmentNo()), Integer.toString(seat),
                                                        resourceId, window.availabilityId()));
                                        Choice choice = new Choice(requirement, resource, window, seat, selected,
                                                interval);
                                        segment.choices().add(choice);
                                        seatChoices.add(selected);
                                        duplicateSeatChoices.computeIfAbsent(segment.segmentNo() + ":" + resourceId,
                                                ignored -> new ArrayList<>()).add(selected);
                                        if (!requirement.holdOnPause())
                                        {
                                            if (resource.exclusive()) exclusiveIntervals.computeIfAbsent(resourceId,
                                                    ignored -> new ArrayList<>()).add(interval);
                                            else capacityIntervals.computeIfAbsent(new WindowKey(resourceId,
                                                            window.availabilityId()), ignored -> new ArrayList<>())
                                                    .add(new DemandInterval(interval,
                                                            scaled(requirement.capacityDemand())));
                                        }
                                    }
                                }
                                if (seatChoices.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                        Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT",
                                        requirement.requirementId(), "carry 后续分段没有可承载的净窗口",
                                        "最小分段、恢复准备、容量与净日历组合后没有可选区间")));
                                model.addEquality(LinearExpr.sum(seatChoices.toArray(BoolVar[]::new)),
                                        segment.present());
                            }
                            if (requirement.holdOnPause())
                                for (Map.Entry<String, BoolVar> entry : masterByResource.entrySet())
                                {
                                    IntVar holdDuration = model.newIntVar(0, horizon, name("carry_hold_duration",
                                            run.executionRunId(), phase.phaseId(), requirement.requirementId(),
                                            Integer.toString(seat), entry.getKey()));
                                    IntervalVar hold = model.newOptionalIntervalVar(phaseStart, holdDuration, phaseEnd,
                                            entry.getValue(), name("carry_hold", run.executionRunId(),
                                                    phase.phaseId(), requirement.requirementId(),
                                                    Integer.toString(seat), entry.getKey()));
                                    exclusiveIntervals.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                                            .add(hold);
                                }
                        }
                        duplicateSeatChoices.values().forEach(values -> model.addAtMostOne(
                                values.stream().map(value -> (Literal) value).toList()));
                    }
                    phases.add(new PhaseVars(task, phase, phaseStart, phaseEnd, duration, segmentVars));
                    continue;
                }
                IntVar start = model.newIntVar(earliest, horizon,
                        name("carry_start", run.executionRunId(), phase.phaseId()));
                IntVar end = model.newIntVar(earliest, horizon,
                        name("carry_end", run.executionRunId(), phase.phaseId()));
                model.addEquality(end, LinearExpr.newBuilder().add(start).add(duration));
                List<Choice> choices = new ArrayList<>();
                for (SolverInput.ResourceRequirement requirement : phase.requirements())
                {
                    Map<String, List<Literal>> byResourceForRequirement = new HashMap<>();
                    for (int seat = 1; seat <= requirement.seatCount(); seat++)
                    {
                        List<Literal> seatChoices = new ArrayList<>();
                        for (String resourceId : requirement.candidateResourceIds().stream().sorted().toList())
                        {
                            SolverInput.Resource resource = resources.get(resourceId);
                            if (resource == null) continue;
                            for (SolverInput.AvailabilityWindow window : windows.getOrDefault(resourceId, List.of()))
                            {
                                if (decimal(window.capacity()).compareTo(decimal(requirement.capacityDemand())) < 0)
                                    continue;
                                int windowStart = axis.ceilOffset(window.startAt());
                                int windowEnd = axis.floorOffset(window.endAt());
                                if (windowEnd - windowStart < duration) continue;
                                BoolVar selected = model.newBoolVar(name("carry_choose", run.executionRunId(),
                                        phase.phaseId(), requirement.requirementId(), Integer.toString(seat),
                                        resourceId, window.availabilityId()));
                                model.addGreaterOrEqual(start, windowStart).onlyEnforceIf(selected);
                                model.addLessOrEqual(end, windowEnd).onlyEnforceIf(selected);
                                IntervalVar interval = model.newOptionalFixedSizeIntervalVar(start, duration, selected,
                                        name("carry_use", run.executionRunId(), phase.phaseId(),
                                                requirement.requirementId(), Integer.toString(seat), resourceId,
                                                window.availabilityId()));
                                Choice choice = new Choice(requirement, resource, window, seat, selected, interval);
                                choices.add(choice);
                                seatChoices.add(selected);
                                byResourceForRequirement.computeIfAbsent(resourceId, ignored -> new ArrayList<>())
                                        .add(selected);
                                if (resource.exclusive())
                                    exclusiveIntervals.computeIfAbsent(resourceId, ignored -> new ArrayList<>())
                                            .add(interval);
                                else
                                    capacityIntervals.computeIfAbsent(new WindowKey(resourceId,
                                                    window.availabilityId()), ignored -> new ArrayList<>())
                                            .add(new DemandInterval(interval, scaled(requirement.capacityDemand())));
                            }
                        }
                        if (seatChoices.isEmpty()) return rejected(input, generatedAt, List.of(problem(
                                Problem.ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT",
                                requirement.requirementId(), "carry 后续阶段没有可承载的净窗口",
                                "资源、容量与净日历组合后没有任何可选区间")));
                        model.addExactlyOne(seatChoices);
                    }
                    byResourceForRequirement.values().forEach(model::addAtMostOne);
                }
                phases.add(new PhaseVars(task, phase, start, end, duration,
                        List.of(new SegmentVars(start, end, null, null, 1, 0, choices))));
            }
            model.addGreaterOrEqual(phases.get(0).start(), earliest);
            for (int index = 1; index < phases.size(); index++)
                model.addLessOrEqual(phases.get(index - 1).end(), phases.get(index).start());
            TaskVars taskVars = new TaskVars(task, phases, members, null);
            carryVarsByRun.put(run.executionRunId(), taskVars);
            for (SolverInput.Task member : members)
                if (carryVarsByTask.putIfAbsent(member.taskId(), taskVars) != null)
                    return rejected(input, generatedAt, List.of(problem(
                            Problem.ReasonCode.CONTRACT_VALIDATION_FAILED, "TASK", member.taskId(),
                            "任务存在多个活动 carry", "同一任务最多只能属于一个非终态执行 run")));
        }
        List<TaskVars> optimizableTasks = allTaskVars(tasks, carryVarsByRun);

        List<IntVar> stabilityDeltas = new ArrayList<>();
        List<BoolVar> stabilityChanges = new ArrayList<>();
        if (input.baseVersion() != null)
        {
            Map<String, TaskVars> currentByMembers = uniqueTaskVars(tasks).stream().collect(
                    java.util.stream.Collectors.toMap(value -> memberKey(value.memberTaskIds()), value -> value));
            for (SolverInput.BaselineJob baseline : input.baseVersion().jobs())
            {
                TaskVars current = currentByMembers.get(memberKey(Set.copyOf(baseline.memberTaskIds())));
                if (current == null)
                    return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.LOCK_CONFLICT,
                            "PLAN_JOB", baseline.baselineJobId(), "基线作业无法映射",
                            "基线成员必须精确对应当前普通作业或固定共享批成员")));
                int baselineStart = exactOffset(axis, baseline.startAt());
                int baselineEnd = exactOffset(axis, baseline.endAt());
                boolean frozen = baseline.startAt().isBefore(input.baseVersion().freezeEndAt());
                if (frozen)
                {
                    model.addEquality(current.start(), baselineStart);
                    model.addEquality(current.end(), baselineEnd);
                }
                else
                {
                    IntVar startDelta = model.newIntVar(0, horizon,
                            name("baseline_start_delta", baseline.baselineJobId()));
                    IntVar endDelta = model.newIntVar(0, horizon,
                            name("baseline_end_delta", baseline.baselineJobId()));
                    model.addGreaterOrEqual(startDelta,
                            LinearExpr.newBuilder().add(current.start()).add(-baselineStart));
                    model.addGreaterOrEqual(startDelta,
                            LinearExpr.newBuilder().addTerm(current.start(), -1).add(baselineStart));
                    model.addGreaterOrEqual(endDelta,
                            LinearExpr.newBuilder().add(current.end()).add(-baselineEnd));
                    model.addGreaterOrEqual(endDelta,
                            LinearExpr.newBuilder().addTerm(current.end(), -1).add(baselineEnd));
                    stabilityDeltas.add(startDelta);
                    stabilityDeltas.add(endDelta);
                }
                Map<String, List<BoolVar>> choicesByResource = current.phases().stream()
                        .flatMap(phase -> phase.choices().stream())
                        .collect(java.util.stream.Collectors.groupingBy(choice -> choice.resource().resourceId(),
                                java.util.stream.Collectors.mapping(Choice::selected,
                                        java.util.stream.Collectors.toList())));
                Set<String> baselineResources = Set.copyOf(baseline.resourceIds());
                if (frozen)
                {
                    for (String resourceId : baselineResources)
                    {
                        List<BoolVar> selected = choicesByResource.getOrDefault(resourceId, List.of());
                        if (selected.isEmpty())
                            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.LOCK_CONFLICT,
                                    "PLAN_JOB", baseline.baselineJobId(), "冻结资源已不可用于基线作业",
                                    "冻结区不允许自动换资源；应先处理现实冲突或走明确例外动作")));
                        model.addBoolOr(selected.stream().map(value -> (Literal) value).toList());
                    }
                    choicesByResource.forEach((resourceId, selected) -> {
                        if (!baselineResources.contains(resourceId))
                            selected.forEach(value -> model.addEquality(value, 0));
                    });
                }
                else
                {
                    Set<String> comparedResources = new java.util.TreeSet<>(baselineResources);
                    comparedResources.addAll(choicesByResource.keySet());
                    for (String resourceId : comparedResources)
                    {
                        List<BoolVar> selected = choicesByResource.getOrDefault(resourceId, List.of());
                        BoolVar used = model.newBoolVar(name("baseline_resource_used",
                                baseline.baselineJobId(), resourceId));
                        if (selected.isEmpty()) model.addEquality(used, 0);
                        else model.addMaxEquality(used, selected.toArray(BoolVar[]::new));
                        if (baselineResources.contains(resourceId))
                        {
                            BoolVar missing = model.newBoolVar(name("baseline_resource_missing",
                                    baseline.baselineJobId(), resourceId));
                            model.addEquality(LinearExpr.sum(new IntVar[] { used, missing }), 1);
                            stabilityChanges.add(missing);
                        }
                        else stabilityChanges.add(used);
                    }
                }
            }
        }

        for (SolverInput.Dependency dependency : input.dependencies())
        {
            TaskVars predecessor = tasks.get(dependency.predecessorTaskId());
            TaskVars carryPredecessor = carryVarsByTask.get(dependency.predecessorTaskId());
            TaskVars successor = tasks.get(dependency.successorTaskId());
            if (successor == null) continue;
            Instant carryCompletion = carry.fixedLastReleaseAt(dependency.predecessorTaskId());
            if (dependency.relationType() == SolverInput.RelationType.FINISH_TO_START)
            {
                if (predecessor != null)
                    model.addGreaterOrEqual(successor.start(), LinearExpr.newBuilder().add(predecessor.end())
                            .add(axis.ceilDurationSeconds(dependency.lagSeconds())));
                if (carryPredecessor != null)
                    model.addGreaterOrEqual(successor.start(), LinearExpr.newBuilder().add(carryPredecessor.end())
                            .add(axis.ceilDurationSeconds(dependency.lagSeconds())));
                if (carryCompletion != null)
                    model.addGreaterOrEqual(successor.start(), axis.ceilOffset(carryCompletion)
                            + axis.ceilDurationSeconds(dependency.lagSeconds()));
                continue;
            }
            if (dependency.relationType() != SolverInput.RelationType.QUANTITY) continue;
            BigDecimal threshold = requiredRelease.get(dependency.dependencyId());
            BigDecimal carried = carry.quantityByTask().getOrDefault(dependency.predecessorTaskId(), BigDecimal.ZERO);
            Instant fixedRelease = carry.fixedReleaseAt(dependency.predecessorTaskId(), threshold.min(carried));
            if (threshold.compareTo(carried) <= 0)
            {
                if (carryPredecessor != null)
                    model.addGreaterOrEqual(successor.start(), LinearExpr.newBuilder().add(carryPredecessor.end())
                            .add(axis.ceilDurationSeconds(dependency.lagSeconds())));
                if (fixedRelease != null)
                    model.addGreaterOrEqual(successor.start(), axis.ceilOffset(fixedRelease)
                            + axis.ceilDurationSeconds(dependency.lagSeconds()));
                continue;
            }
            if (predecessor == null) continue;
            BigDecimal futureThreshold = threshold.subtract(carried);
            IntVar predecessorPoint = predecessor.end();
            int releaseOffset = 0;
            if (dependency.relationType() == SolverInput.RelationType.QUANTITY)
            {
                if (predecessor.sharedBatch() == null)
                {
                    PhaseVars production = predecessor.productionPhase();
                    predecessorPoint = production.start();
                    releaseOffset = releaseOffsetUnits(predecessor.task(), production.phase(),
                            futureThreshold, axis);
                }
            }
            LinearExprBuilder allowed = LinearExpr.newBuilder().add(predecessorPoint)
                    .add(Math.addExact(releaseOffset, axis.ceilDurationSeconds(dependency.lagSeconds())));
            model.addGreaterOrEqual(successor.start(), allowed);
            if (carryPredecessor != null)
                model.addGreaterOrEqual(successor.start(), LinearExpr.newBuilder().add(carryPredecessor.end())
                        .add(axis.ceilDurationSeconds(dependency.lagSeconds())));
            if (carryCompletion != null)
                model.addGreaterOrEqual(successor.start(), axis.ceilOffset(carryCompletion)
                        + axis.ceilDurationSeconds(dependency.lagSeconds()));
        }
        for (SolverInput.MaterialDemand demand : input.materialDemands())
        {
            if (demand.materialStatus() == SolverInput.MaterialStatus.CANCELLED) continue;
            TaskVars successor = tasks.get(demand.targetTaskId());
            if (successor == null) continue;
            if (demand.sourceTaskId() == null)
            {
                Instant ready = materialRelease.externalReadyAtByDemand().get(demand.demandId());
                if (ready != null) model.addGreaterOrEqual(successor.start(), ready.isBefore(input.horizon().startAt())
                        ? 0 : axis.ceilOffset(ready));
                continue;
            }
            TaskVars predecessor = tasks.get(demand.sourceTaskId());
            TaskVars carryPredecessor = carryVarsByTask.get(demand.sourceTaskId());
            Instant actualReady = materialRelease.internalReadyAtByDemand().get(demand.demandId());
            if (actualReady != null)
            {
                model.addGreaterOrEqual(successor.start(), actualReady.isBefore(input.horizon().startAt())
                        ? 0 : axis.ceilOffset(actualReady));
                continue;
            }
            BigDecimal required = materialRelease.internalRequiredByDemand().get(demand.demandId());
            BigDecimal carried = carry.quantityByTask().getOrDefault(demand.sourceTaskId(), BigDecimal.ZERO);
            Instant carryReady = carry.fixedReleaseAt(demand.sourceTaskId(), required.min(carried));
            if (required.compareTo(carried) <= 0)
            {
                if (carryPredecessor != null)
                    model.addGreaterOrEqual(successor.start(), carryPredecessor.end());
                if (carryReady != null) model.addGreaterOrEqual(successor.start(), axis.ceilOffset(carryReady));
                continue;
            }
            if (predecessor == null) continue;
            IntVar releasePoint = predecessor.end();
            int releaseOffset = 0;
            if (predecessor.sharedBatch() == null)
            {
                PhaseVars production = predecessor.productionPhase();
                releasePoint = production.start();
                releaseOffset = releaseOffsetUnits(predecessor.task(), production.phase(),
                        required.subtract(carried), axis);
            }
            model.addGreaterOrEqual(successor.start(), LinearExpr.newBuilder().add(releasePoint).add(releaseOffset));
            if (carryPredecessor != null)
                model.addGreaterOrEqual(successor.start(), carryPredecessor.end());
            Instant carryCompletion = carry.fixedLastReleaseAt(demand.sourceTaskId());
            if (carryCompletion != null)
                model.addGreaterOrEqual(successor.start(), axis.ceilOffset(carryCompletion));
        }
        LockTargets lockTargets = indexLockTargets(input, tasks, requiredRelease, materialRelease, axis);
        for (SolverInput.PlanLock lock : input.locks())
        {
            LockableTarget target = switch (lock.targetType())
            {
                case "JOB" -> lockTargets.jobs().get(lock.targetId());
                case "SEGMENT" -> lockTargets.segments().get(lock.targetId());
                case "ALLOCATION" -> lockTargets.allocations().get(lock.targetId());
                default -> null;
            };
            if (target == null)
                return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.LOCK_CONFLICT,
                        "PLAN_LOCK", lock.lockId(), "锁定目标不存在或当前模型不能稳定映射",
                        "锁必须解析到本次候选的确定作业、分段或资源分配，不能自动解锁或提升粒度")));
            if (target.present() != null) model.addEquality(target.present(), 1);
            if (!target.identityChoices().isEmpty())
                model.addBoolOr(target.identityChoices().stream().map(Choice::selected)
                        .map(value -> (Literal) value).toList());
            if ("TIME".equals(lock.lockType()) || "FULL".equals(lock.lockType()))
            {
                try
                {
                    model.addEquality(target.start(), Math.subtractExact(exactOffset(axis, lock.lockedStartAt()),
                            target.startOffset()));
                    model.addEquality(target.end(), Math.subtractExact(exactOffset(axis, lock.lockedEndAt()),
                            target.endOffset()));
                }
                catch (IllegalArgumentException | ArithmeticException exception)
                {
                    return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.LOCK_CONFLICT,
                            "PLAN_LOCK", lock.lockId(), "锁定时间无法进入当前时间轴", exception.getMessage())));
                }
            }
            if ("RESOURCE".equals(lock.lockType()) || "FULL".equals(lock.lockType()))
            {
                for (String resourceId : lock.lockedResourceIds())
                {
                    List<Literal> selected = target.choices().stream()
                            .filter(choice -> choice.resource().resourceId().equals(resourceId))
                            .map(Choice::selected).map(value -> (Literal) value).toList();
                    if (selected.isEmpty())
                        return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.LOCK_CONFLICT,
                                "PLAN_LOCK", lock.lockId(), "锁定资源不可用于目标",
                                "锁定资源不在目标粒度的候选集合中，不能自动换资源")));
                    model.addBoolOr(selected);
                }
            }
        }
        for (SolverInput.ActualOccupancy occupancy : input.actualOccupancies())
        {
            SolverInput.Resource resource = resources.get(occupancy.resourceId());
            if (resource == null) continue;
            Instant occupiedEnd = occupancy.endAt() != null ? occupancy.endAt()
                    : occupancy.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                            ? occupancy.releaseAt() : input.horizon().endAt();
            Instant startAt = occupancy.startAt().isBefore(input.horizon().startAt())
                    ? input.horizon().startAt() : occupancy.startAt();
            Instant endAt = occupiedEnd.isAfter(input.horizon().endAt()) ? input.horizon().endAt() : occupiedEnd;
            int start = axis.floorOffset(startAt), end = axis.ceilOffset(endAt);
            if (end <= start) continue;
            if (resource.exclusive())
                exclusiveIntervals.computeIfAbsent(resource.resourceId(), ignored -> new ArrayList<>())
                        .add(model.newFixedInterval(start, end - start, name("actual", occupancy.occupancyId())));
            else
                for (SolverInput.AvailabilityWindow window : windows.getOrDefault(resource.resourceId(), List.of()))
                {
                    Instant clippedStart = startAt.isAfter(window.startAt()) ? startAt : window.startAt();
                    Instant clippedEnd = endAt.isBefore(window.endAt()) ? endAt : window.endAt();
                    if (!clippedEnd.isAfter(clippedStart)) continue;
                    int left = axis.floorOffset(clippedStart), right = axis.ceilOffset(clippedEnd);
                    IntervalVar interval = model.newFixedInterval(left, right - left,
                            name("actual", occupancy.occupancyId(), window.availabilityId()));
                    capacityIntervals.computeIfAbsent(new WindowKey(resource.resourceId(), window.availabilityId()),
                            ignored -> new ArrayList<>()).add(new DemandInterval(interval, scaled(window.capacity())));
                }
        }
        exclusiveIntervals.values().forEach(model::addNoOverlap);
        capacityIntervals.forEach((key, values) -> {
            SolverInput.AvailabilityWindow window = values.stream().map(DemandInterval::interval)
                    .findFirst().flatMap(ignored -> windows.getOrDefault(key.resourceId(), List.of()).stream()
                            .filter(value -> value.availabilityId().equals(key.windowId())).findFirst()).orElseThrow();
            var cumulative = model.addCumulative(scaled(window.capacity()));
            values.forEach(value -> cumulative.addDemand(value.interval(), value.demand()));
        });
        Map<TaskVars, GreedyHint> independentGreedyHints = addIndependentSinglePhaseGreedyHint(
                model, input, axis, optimizableTasks);
        boolean useLargeIndependentFirstFeasible = independentGreedyHints.size()
                >= LARGE_INDEPENDENT_HINT_THRESHOLD;
        LinearExprBuilder objective = LinearExpr.newBuilder();
        long completionUpperBound;
        long stabilityUpperBound;
        long stabilityWeight;
        long tardinessWeight;
        try
        {
            completionUpperBound = Math.multiplyExact((long) optimizableTasks.size(), horizon);
            stabilityUpperBound = Math.addExact(
                    Math.multiplyExact((long) stabilityDeltas.size(), horizon), stabilityChanges.size());
            stabilityWeight = Math.addExact(completionUpperBound, 1L);
            long weightedStabilityUpperBound = Math.multiplyExact(stabilityUpperBound, stabilityWeight);
            tardinessWeight = Math.addExact(Math.addExact(weightedStabilityUpperBound, completionUpperBound), 1L);
            long weightedTardinessUpperBound = Math.multiplyExact(completionUpperBound, tardinessWeight);
            Math.addExact(Math.addExact(weightedTardinessUpperBound, weightedStabilityUpperBound),
                    completionUpperBound);
        }
        catch (ArithmeticException exception)
        {
            return rejected(input, generatedAt, List.of(problem(Problem.ReasonCode.CAPABILITY_NOT_IMPLEMENTED,
                    "PLAN_VERSION", input.planVersionId(), "分层目标整数范围溢出",
                    "请缩小任务范围或 horizon，避免 CP-SAT 目标系数超过 int64")));
        }
        for (TaskVars value : optimizableTasks)
        {
            if (value.task().promisedAt() != null)
            {
                int due = Math.max(0, Math.min(horizon, axis.floorOffset(value.task().promisedAt())));
                IntVar tardiness = model.newIntVar(0, horizon, name("tardiness", value.task().taskId()));
                model.addMaxEquality(tardiness, List.of(
                        LinearExpr.newBuilder().add(value.end()).add(-due), LinearExpr.constant(0)));
                GreedyHint greedyHint = independentGreedyHints.get(value);
                if (greedyHint != null) model.addHint(tardiness, Math.max(0, greedyHint.end() - due));
                objective.addTerm(tardiness, tardinessWeight);
            }
            objective.add(value.end());
        }
        stabilityDeltas.forEach(value -> objective.addTerm(value, stabilityWeight));
        stabilityChanges.forEach(value -> objective.addTerm(value, stabilityWeight));
        model.minimize(objective);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(input.parameters().maxSolveSeconds())
                .setNumSearchWorkers(input.parameters().solverSearchThreads())
                .setRandomSeed(input.parameters().randomSeed())
                .setAbsoluteGapLimit(input.parameters().absoluteGapLimit())
                .setRelativeGapLimit(input.parameters().relativeGapLimit());
        if (useLargeIndependentFirstFeasible)
            solver.getParameters().setCpModelPresolve(false).setStopAfterFirstSolution(true);
        AtomicBoolean solving = new AtomicBoolean(true);
        Thread cancellationWatcher = new Thread(() -> {
            while (solving.get())
            {
                if (cancellationRequested.getAsBoolean())
                {
                    solver.stopSearch();
                    return;
                }
                LockSupport.parkNanos(50_000_000L);
            }
        }, "aps-cpsat-cancellation");
        cancellationWatcher.setDaemon(true);
        cancellationWatcher.start();
        CpSolverStatus status;
        try { status = solver.solve(model); }
        finally
        {
            solving.set(false);
            cancellationWatcher.interrupt();
        }
        long wallMillis = Math.max(0L, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        if (cancellationRequested.getAsBoolean()) return cancelled(input, generatedAt);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE)
            return noSolution(input, generatedAt, status, wallMillis, model);

        PlanCandidate candidate = candidate(input, axis, solver, tasks, requiredRelease, materialRelease, carry,
                carryVarsByRun);
        String candidateHash = codec.candidateHash(candidate);
        ValidationResult replay = validator.validateCandidate(input, candidate, candidateHash,
                input.definitionRevision(), input.executionRevision(), ValidationResult.Scope.PLAN_CANDIDATE, generatedAt);
        if (replay.validationStatus() != ValidationResult.Status.PASS)
            return invalidCandidate(input, generatedAt, wallMillis, model, replay.problems());
        return feasible(input, generatedAt, wallMillis, model, solver, status, useLargeIndependentFirstFeasible,
                candidateHash, candidate, axis, optimizableTasks,
                stabilityDeltas, stabilityChanges);
    }

    /**
     * 为互不依赖的单阶段独占资源任务提供一个完整、确定性的可行起点。
     *
     * <p>提示不新增或放宽任何约束，CP-SAT 仍负责求解，最终候选仍由独立校验器复验。
     * 仅在没有基线、锁、依赖、物料、实际占用、共享批和可中断阶段时启用；复杂模型保持原搜索路径。
     * 先在内存中完成全部排放，任何任务不能放入净窗口时整批放弃提示，避免向模型提交半套猜测。
     * 达到大规模阈值时关闭昂贵的 presolve 并在第一个受约束验证的可行解停止；因此结果保持 FEASIBLE，
     * 不会被误标为 OPTIMAL。</p>
     */
    private Map<TaskVars, GreedyHint> addIndependentSinglePhaseGreedyHint(CpModel model, SolverInput input,
            TimeAxis axis,
            List<TaskVars> taskVars)
    {
        if (input.baseVersion() != null || !input.dependencies().isEmpty() || !input.materialDemands().isEmpty()
                || !input.actualOccupancies().isEmpty() || !input.sharedBatchCandidates().isEmpty()
                || !input.locks().isEmpty() || taskVars.size() != input.tasks().stream()
                        .filter(value -> value.planningClass() == SolverInput.PlanningClass.MANDATORY_DETAIL).count())
            return Map.of();
        if (taskVars.stream().anyMatch(value -> value.phases().size() != 1
                || value.phases().get(0).phase().interruptible()
                || value.phases().get(0).segments().size() != 1
                || value.phases().get(0).phase().requirements().size() != 1
                || value.phases().get(0).phase().requirements().get(0).seatCount() != 1
                || value.phases().get(0).choices().isEmpty()
                || value.phases().get(0).choices().stream().anyMatch(choice -> !choice.resource().exclusive())))
            return Map.of();

        Map<String, Integer> nextFreeByResource = new HashMap<>();
        Map<TaskVars, GreedyHint> hints = new LinkedHashMap<>();
        List<TaskVars> ordered = taskVars.stream().sorted(Comparator
                .comparing((TaskVars value) -> value.task().promisedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(value -> value.task().earliestStartAt())
                .thenComparing(value -> value.task().taskId())).toList();
        for (TaskVars task : ordered)
        {
            PhaseVars phase = task.phases().get(0);
            int earliest = axis.ceilOffset(task.task().earliestStartAt());
            GreedyHint best = null;
            for (Choice choice : phase.choices())
            {
                int windowStart = axis.ceilOffset(choice.window().startAt());
                int windowEnd = axis.floorOffset(choice.window().endAt());
                int start = Math.max(Math.max(earliest, windowStart),
                        nextFreeByResource.getOrDefault(choice.resource().resourceId(), windowStart));
                int end = Math.addExact(start, phase.duration());
                if (end > windowEnd) continue;
                GreedyHint candidate = new GreedyHint(task, phase, choice, start, end);
                if (best == null || candidate.end() < best.end()
                        || candidate.end() == best.end() && candidate.choice().resource().resourceId()
                                .compareTo(best.choice().resource().resourceId()) < 0
                        || candidate.end() == best.end()
                                && candidate.choice().resource().resourceId()
                                        .equals(best.choice().resource().resourceId())
                                && candidate.choice().window().availabilityId()
                                        .compareTo(best.choice().window().availabilityId()) < 0)
                    best = candidate;
            }
            if (best == null) return Map.of();
            hints.put(task, best);
            nextFreeByResource.put(best.choice().resource().resourceId(), best.end());
        }

        for (GreedyHint hint : hints.values())
        {
            model.addHint(hint.phase().start(), hint.start());
            model.addHint(hint.phase().end(), hint.end());
            for (Choice choice : hint.phase().choices()) model.addHint(choice.selected(), choice == hint.choice());
        }
        return Map.copyOf(hints);
    }

    private PlanCandidate candidate(SolverInput input, TimeAxis axis, CpSolver solver, Map<String, TaskVars> taskVars,
            Map<String, BigDecimal> requiredRelease, MaterialReleaseMath.ReleasePlan materialRelease,
            CarryProjection carry, Map<String, TaskVars> carryVarsByRun)
    {
        List<PlanCandidate.Job> jobs = new ArrayList<>();
        List<PlanCandidate.Segment> segments = new ArrayList<>();
        List<PlanCandidate.Allocation> allocations = new ArrayList<>();
        for (TaskVars value : uniqueTaskVars(taskVars))
        {
            SolverInput.Task task = value.task();
            String jobId = stableId("job", input.planVersionId(), value.stableSourceId());
            Instant jobStart = axis.instantAt(solver.value(value.start()));
            Instant jobEnd = axis.instantAt(solver.value(value.end()));
            jobs.add(new PlanCandidate.Job(jobId, value.jobType(),
                    task.operationSpecId(), task.workCenterId(), task.quantity(), task.uomCode(), jobStart, jobEnd,
                    value.memberTasks().stream().map(SolverInput.Task::taskId).sorted().toList()));
            int phaseNo = 0;
            int segmentNo = 0;
            for (PhaseVars phase : value.phases())
            {
                phaseNo++;
                if (phase.phase().interruptible())
                {
                    for (SegmentVars physical : phase.segments())
                    {
                        if (!solver.booleanValue(physical.present())) continue;
                        Instant physicalStart = axis.instantAt(solver.value(physical.start()));
                        Instant physicalEnd = axis.instantAt(solver.value(physical.end()));
                        String segmentId = stableId("segment", jobId, phase.phase().phaseId(),
                                Integer.toString(phaseNo), Integer.toString(++segmentNo));
                        segments.add(new PlanCandidate.Segment(segmentId, jobId, phase.phase().phaseId(),
                                phase.phase().phaseType(), segmentNo, physicalStart, physicalEnd,
                                task.quantity(), null, null));
                        for (Choice choice : physical.choices())
                            if (solver.booleanValue(choice.selected()))
                                allocations.add(new PlanCandidate.Allocation(stableId("allocation", segmentId,
                                        choice.requirement().requirementId(), Integer.toString(choice.seat()),
                                        choice.resource().resourceId()), segmentId, choice.requirement().requirementId(),
                                        choice.resource().resourceId(), choice.resource().resourceType(), choice.seat(),
                                        choice.requirement().capacityDemand()));
                    }
                    continue;
                }
                Instant start = axis.instantAt(solver.value(phase.start()));
                Instant end = axis.instantAt(solver.value(phase.end()));
                List<SolverInput.Dependency> outgoing = input.dependencies().stream()
                        .filter(dependency -> dependency.relationType() == SolverInput.RelationType.QUANTITY)
                        .filter(dependency -> value.memberTaskIds().contains(dependency.predecessorTaskId())).toList();
                List<SolverInput.MaterialDemand> outgoingMaterial = input.materialDemands().stream()
                        .filter(demand -> demand.sourceTaskId() != null
                                && value.memberTaskIds().contains(demand.sourceTaskId())).toList();
                boolean productionPhase = value.sharedBatch() == null && (!outgoing.isEmpty() || !outgoingMaterial.isEmpty())
                        && phase == value.productionPhase();
                boolean sharedRelease = value.sharedBatch() != null && phaseNo == value.phases().size();
                List<SegmentDraft> drafts = productionPhase
                        ? releaseSegments(task, phase.phase(), solver.value(phase.start()), axis, outgoing,
                                requiredRelease, outgoingMaterial, materialRelease)
                        : List.of(new SegmentDraft(start, end, task.quantity(), sharedRelease ? end : null,
                                sharedRelease ? task.quantity() : null));
                for (SegmentDraft draft : drafts)
                {
                    String segmentId = stableId("segment", jobId, phase.phase().phaseId(), Integer.toString(phaseNo),
                            Integer.toString(++segmentNo));
                    segments.add(new PlanCandidate.Segment(segmentId, jobId, phase.phase().phaseId(),
                            phase.phase().phaseType(), segmentNo, draft.startAt(), draft.endAt(),
                            draft.plannedQuantity(), draft.releaseAt(), draft.releaseQuantity()));
                    for (Choice choice : phase.choices())
                        if (solver.booleanValue(choice.selected()))
                            allocations.add(new PlanCandidate.Allocation(stableId("allocation", segmentId,
                                    choice.requirement().requirementId(), Integer.toString(choice.seat()),
                                    choice.resource().resourceId()), segmentId, choice.requirement().requirementId(),
                                    choice.resource().resourceId(), choice.resource().resourceType(), choice.seat(),
                                    choice.requirement().capacityDemand()));
                }
            }
        }
        appendCarryCandidate(input, axis, solver, carry, carryVarsByRun, jobs, segments, allocations);
        return new PlanCandidate(input.horizon().startAt(), input.horizon().endAt(),
                jobs.stream().sorted(Comparator.comparing(PlanCandidate.Job::jobId)).toList(),
                segments.stream().sorted(Comparator.comparing(PlanCandidate.Segment::jobId)
                        .thenComparingInt(PlanCandidate.Segment::segmentNo)).toList(),
                allocations.stream().sorted(Comparator.comparing(PlanCandidate.Allocation::allocationId)).toList(),
                input.tasks().stream().filter(value -> value.planningClass() == SolverInput.PlanningClass.FUTURE_CARRY_FORWARD)
                        .map(SolverInput.Task::taskId).sorted().toList(), List.of());
    }

    private List<SegmentDraft> releaseSegments(SolverInput.Task task, SolverInput.Phase phase, long phaseStartUnit,
            TimeAxis axis, List<SolverInput.Dependency> outgoing,
            Map<String, BigDecimal> requiredRelease, List<SolverInput.MaterialDemand> outgoingMaterial,
            MaterialReleaseMath.ReleasePlan materialRelease)
    {
        Set<BigDecimal> milestones = new java.util.TreeSet<>(
                QuantityReleaseMath.releaseMilestones(task, outgoing, requiredRelease));
        milestones.addAll(MaterialReleaseMath.releaseMilestones(task, outgoingMaterial, materialRelease));
        Map<Integer, BigDecimal> cumulativeByOffset = new java.util.TreeMap<>();
        for (BigDecimal milestone : milestones)
        {
            int offset = releaseOffsetUnits(task, phase, milestone, axis);
            cumulativeByOffset.merge(offset, milestone, BigDecimal::max);
        }
        List<SegmentDraft> result = new ArrayList<>();
        int previousOffset = 0;
        BigDecimal previousQuantity = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : cumulativeByOffset.entrySet())
        {
            int offset = entry.getKey();
            BigDecimal increment = entry.getValue().subtract(previousQuantity);
            if (offset <= previousOffset || increment.signum() <= 0) continue;
            Instant start = axis.instantAt(Math.addExact(phaseStartUnit, previousOffset));
            Instant end = axis.instantAt(Math.addExact(phaseStartUnit, offset));
            result.add(new SegmentDraft(start, end, decimal(increment), end, decimal(increment)));
            previousOffset = offset;
            previousQuantity = entry.getValue();
        }
        if (result.isEmpty()) throw new IllegalStateException("数量释放阶段没有形成正时长分段: " + task.taskId());
        return List.copyOf(result);
    }

    private SolverResult feasible(SolverInput input, Instant generatedAt, long wallMillis, CpModel model,
            CpSolver solver, CpSolverStatus status, boolean firstFeasibleMode, String candidateHash,
            PlanCandidate candidate, TimeAxis axis, List<TaskVars> tasks, List<IntVar> stabilityDeltas,
            List<BoolVar> stabilityChanges)
    {
        boolean optimal = status == CpSolverStatus.OPTIMAL;
        SolverResult.SolverStatus solverStatus = optimal ? SolverResult.SolverStatus.OPTIMAL : SolverResult.SolverStatus.FEASIBLE;
        SolverResult.ResultKind kind = optimal || firstFeasibleMode
                ? SolverResult.ResultKind.FEASIBLE : SolverResult.ResultKind.TIMEOUT_WITH_SOLUTION;
        SolverResult.StopReason stop = optimal || firstFeasibleMode
                ? SolverResult.StopReason.COMPLETED : SolverResult.StopReason.TIME_LIMIT;
        return result(input, generatedAt, SolverResult.PlanStatus.FEASIBLE, solverStatus, kind,
                summary(stop, wallMillis, model, input, solver.objectiveValue(), solver.bestObjectiveBound(), optimal,
                        objectiveBreakdown(solver, axis, tasks, stabilityDeltas, stabilityChanges, optimal)),
                candidateHash, candidate, List.of());
    }

    private SolverResult rejected(SolverInput input, Instant at, List<Problem> problems)
    {
        return result(input, at, SolverResult.PlanStatus.CONFLICT, null, SolverResult.ResultKind.INVALID_INPUT,
                emptySummary(SolverResult.StopReason.INPUT_REJECTED, input), null, null, problems);
    }

    private SolverResult cancelled(SolverInput input, Instant at)
    {
        return result(input, at, SolverResult.PlanStatus.CANCELLED, SolverResult.SolverStatus.UNKNOWN, null,
                emptySummary(SolverResult.StopReason.CANCELLED, input), null, null, List.of());
    }

    private SolverResult noSolution(SolverInput input, Instant at, CpSolverStatus status, long wallMillis, CpModel model)
    {
        boolean proven = status == CpSolverStatus.INFEASIBLE;
        Problem problem = problem(proven ? Problem.ReasonCode.NO_COMMON_WINDOW : Problem.ReasonCode.TIME_LIMIT_NO_SOLUTION,
                "PLAN_VERSION", input.planVersionId(), proven ? "模型在当前输入下无可行组合" : "时限内未找到可行候选",
                proven ? "CP-SAT 已证明硬约束组合不可行" : "UNKNOWN 不等于已证明不可行，可调整时限后重试");
        return result(input, at, SolverResult.PlanStatus.CONFLICT,
                proven ? SolverResult.SolverStatus.INFEASIBLE : SolverResult.SolverStatus.UNKNOWN,
                proven ? SolverResult.ResultKind.INFEASIBLE_PROVEN : SolverResult.ResultKind.TIMEOUT_NO_SOLUTION,
                summary(proven ? SolverResult.StopReason.COMPLETED : SolverResult.StopReason.TIME_LIMIT, wallMillis,
                        model, input, null, null, false, List.of()), null, null, List.of(problem));
    }

    private SolverResult invalidCandidate(SolverInput input, Instant at, long wallMillis, CpModel model,
            List<Problem> problems)
    {
        return result(input, at, SolverResult.PlanStatus.FAILED, SolverResult.SolverStatus.MODEL_INVALID, null,
                summary(SolverResult.StopReason.MODEL_INVALID, wallMillis, model, input, null, null, false, List.of()),
                null, null, problems);
    }

    private SolverResult result(SolverInput input, Instant at, SolverResult.PlanStatus planStatus,
            SolverResult.SolverStatus solverStatus, SolverResult.ResultKind kind, SolverResult.SolverSummary summary,
            String candidateHash, PlanCandidate candidate, List<Problem> problems)
    {
        return new SolverResult("1.0", "SOLVER_RESULT", input.requestId(), input.planVersionId(), at,
                input.definitionRevision(), input.executionRevision(), input.inputHash(), MODEL_VERSION, SOLVER_VERSION,
                planStatus, solverStatus, kind, summary, candidateHash, candidate, problems);
    }

    private SolverResult.SolverSummary emptySummary(SolverResult.StopReason reason, SolverInput input)
    {
        return new SolverResult.SolverSummary(reason, 0, null, null, null, null, null, 0, 0,
                input.parameters().solverSearchThreads(), input.parameters().randomSeed(), List.of());
    }

    private SolverResult.SolverSummary summary(SolverResult.StopReason reason, long wallMillis, CpModel model,
            SolverInput input, Double objective, Double bound, boolean optimal,
            List<SolverResult.Objective> objectives)
    {
        Double gap = objective == null || bound == null ? null : Math.abs(objective - bound);
        Double relativeGap = null;
        if (gap != null) relativeGap = objective.doubleValue() == 0d ? gap : gap / Math.abs(objective);
        return new SolverResult.SolverSummary(reason, wallMillis, objective == null ? null : wallMillis,
                objective, bound, gap, relativeGap, model.model().getVariablesCount(), model.model().getConstraintsCount(),
                input.parameters().solverSearchThreads(), input.parameters().randomSeed(), objectives);
    }

    private List<SolverResult.Objective> objectiveBreakdown(CpSolver solver, TimeAxis axis,
            List<TaskVars> tasks, List<IntVar> stabilityDeltas,
            List<BoolVar> stabilityChanges, boolean optimal)
    {
        double tardiness = 0;
        double completion = 0;
        for (TaskVars value : tasks)
        {
            long end = solver.value(value.end());
            completion += end;
            if (value.task().promisedAt() != null)
                tardiness += Math.max(0, end - axis.floorOffset(value.task().promisedAt()));
        }
        double stability = stabilityDeltas.stream().mapToLong(solver::value).sum()
                + stabilityChanges.stream().filter(solver::booleanValue).count();
        List<SolverResult.Objective> result = new ArrayList<>();
        result.add(new SolverResult.Objective(SolverResult.ObjectiveLevel.L0,
                "HARD_CONSTRAINT_FEASIBILITY", 0d, 0d, true));
        result.add(new SolverResult.Objective(SolverResult.ObjectiveLevel.L1,
                "TOTAL_TARDINESS_UNITS", tardiness, null, optimal));
        result.add(new SolverResult.Objective(SolverResult.ObjectiveLevel.L2,
                "UNPLANNED_MANDATORY_TASKS", 0d, 0d, true));
        if (!stabilityDeltas.isEmpty() || !stabilityChanges.isEmpty())
            result.add(new SolverResult.Objective(SolverResult.ObjectiveLevel.L2,
                    "BASELINE_DISTURBANCE_UNITS", stability, null, optimal));
        result.add(new SolverResult.Objective(SolverResult.ObjectiveLevel.L4,
                "TOTAL_COMPLETION_UNITS", completion, null, optimal));
        return List.copyOf(result);
    }

    private Problem problem(Problem.ReasonCode code, String type, String id, String title, String detail)
    {
        String safeId = validUuid(id) ? id : "00000000-0000-4000-8000-000000000000";
        return new Problem("1.0", "APS_PROBLEM", stableId("problem", code.name(), type, safeId), code, null,
                Problem.Severity.ERROR, title, detail, false, List.of(new Problem.ObjectRef(type, safeId, null)), null,
                Map.of());
    }

    private int durationUnits(SolverInput.Task task, SolverInput.Phase phase, TimeAxis axis)
    {
        BigDecimal seconds = BigDecimal.valueOf(phase.fixedSeconds())
                .add(decimal(phase.secondsPerUnit()).multiply(decimal(task.quantity())));
        return axis.ceilDurationSeconds(seconds.setScale(0, RoundingMode.CEILING).longValueExact());
    }

    private SolverInput.Task combinedTask(SolverInput.SharedBatchCandidate batch, List<SolverInput.Task> members)
    {
        if (members.size() < 2) throw new IllegalArgumentException("共享批次至少需要两个成员");
        SolverInput.Task first = members.get(0);
        Set<String> phaseSignature = first.phases().stream().map(value -> value.phaseId() + ':' + value.sequenceNo()
                + ':' + value.phaseType()).collect(java.util.stream.Collectors.toSet());
        boolean incompatible = members.stream().anyMatch(member -> !member.operationSpecId().equals(first.operationSpecId())
                || !member.workCenterId().equals(first.workCenterId()) || !member.uomCode().equals(first.uomCode())
                || !member.phases().stream().map(value -> value.phaseId() + ':' + value.sequenceNo() + ':' + value.phaseType())
                        .collect(java.util.stream.Collectors.toSet()).equals(phaseSignature));
        if (incompatible) throw new IllegalArgumentException("共享批次成员的工序阶段快照不一致");
        BigDecimal quantity = batch.members().stream().map(value -> decimal(value.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant earliest = members.stream().map(SolverInput.Task::earliestStartAt).max(Comparator.naturalOrder()).orElseThrow();
        Instant promised = members.stream().map(SolverInput.Task::promisedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        return new SolverInput.Task(batch.candidateId(), first.orderLineId(), first.operationSpecId(), first.workCenterId(),
                SolverInput.PlanningClass.MANDATORY_DETAIL, decimal(quantity), first.uomCode(), earliest, promised,
                first.phases());
    }

    private SolverInput.Task withQuantity(SolverInput.Task task, BigDecimal quantity)
    {
        return new SolverInput.Task(task.taskId(), task.orderLineId(), task.operationSpecId(), task.workCenterId(),
                task.planningClass(), decimal(quantity), task.uomCode(), task.earliestStartAt(), task.promisedAt(),
                task.phases());
    }

    private CarryProjection carryProjection(SolverInput input, Map<String, SolverInput.Task> tasks)
    {
        Map<String, List<SolverInput.ActualOccupancy>> grouped = input.actualOccupancies().stream()
                .filter(value -> value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED)
                .filter(value -> value.endAt() == null && !value.members().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(SolverInput.ActualOccupancy::executionRunId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<String, BigDecimal> byTask = new LinkedHashMap<>();
        List<CarryRun> runs = new ArrayList<>();
        for (Map.Entry<String, List<SolverInput.ActualOccupancy>> entry : grouped.entrySet())
        {
            List<SolverInput.ActualOccupancy> occupancies = entry.getValue().stream()
                    .sorted(Comparator.comparing(SolverInput.ActualOccupancy::occupancyId)).toList();
            SolverInput.ActualOccupancy first = occupancies.get(0);
            if (first.currentPhase() == null || first.releaseAt() == null
                    || !first.releaseAt().isAfter(input.horizon().planningAnchorAt())
                    || first.releaseAt().isAfter(input.horizon().endAt()))
                throw new IllegalArgumentException("可信 carry 的预计释放必须位于 planningAnchorAt 与 horizon.endAt 之间: "
                        + entry.getKey());
            List<SolverInput.ActualOccupancyMember> members = first.members().stream()
                    .sorted(Comparator.comparing(SolverInput.ActualOccupancyMember::taskId)).toList();
            if (occupancies.stream().anyMatch(value -> !members.equals(value.members().stream()
                    .sorted(Comparator.comparing(SolverInput.ActualOccupancyMember::taskId)).toList())
                    || !Objects.equals(first.currentPhase(), value.currentPhase())
                    || !Objects.equals(first.releaseAt(), value.releaseAt())))
                throw new IllegalArgumentException("同一 run 的成员、阶段或预计释放不一致: " + entry.getKey());
            for (SolverInput.ActualOccupancyMember member : members)
            {
                SolverInput.Task task = tasks.get(member.taskId());
                if (task == null || !task.uomCode().equals(member.uomCode()))
                    throw new IllegalArgumentException("carry 成员不属于当前输入或单位不一致: " + member.taskId());
                BigDecimal quantity = decimal(member.remainingQuantity());
                if (byTask.containsKey(member.taskId()))
                    throw new IllegalArgumentException("同一任务存在多个活动 carry: " + member.taskId());
                byTask.put(member.taskId(), quantity);
                BigDecimal total = quantity;
                if (total.compareTo(decimal(task.quantity())) > 0)
                    throw new IllegalArgumentException("carry 剩余量超过任务待排量: " + member.taskId());
            }
            SolverInput.Task firstTask = tasks.get(members.get(0).taskId());
            int currentSequence = firstTask.phases().stream()
                    .filter(value -> value.phaseId().equals(first.currentPhase().phaseId()))
                    .mapToInt(SolverInput.Phase::sequenceNo).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("carry 当前阶段不属于任务: " + entry.getKey()));
            List<SolverInput.Phase> laterPhases = firstTask.phases().stream()
                    .filter(value -> value.sequenceNo() > currentSequence)
                    .sorted(Comparator.comparingInt(SolverInput.Phase::sequenceNo)).toList();
            List<String> expectedLater = laterPhases.stream().map(this::phaseIdentity).toList();
            boolean inconsistentLater = members.stream().map(SolverInput.ActualOccupancyMember::taskId)
                    .map(tasks::get).anyMatch(task -> !task.phases().stream()
                            .filter(value -> value.sequenceNo() > currentSequence)
                            .sorted(Comparator.comparingInt(SolverInput.Phase::sequenceNo))
                            .map(this::phaseIdentity).toList().equals(expectedLater));
            if (inconsistentLater)
                throw new IllegalArgumentException("carry 成员的后续阶段快照不一致: " + entry.getKey());
            runs.add(new CarryRun(entry.getKey(), first.currentPhase(), input.horizon().planningAnchorAt(),
                    first.releaseAt(), members, occupancies, laterPhases));
        }
        return new CarryProjection(List.copyOf(runs), Map.copyOf(byTask));
    }

    private void appendCarryCandidate(SolverInput input, TimeAxis axis, CpSolver solver, CarryProjection carry,
            Map<String, TaskVars> carryVarsByRun, List<PlanCandidate.Job> jobs,
            List<PlanCandidate.Segment> segments, List<PlanCandidate.Allocation> allocations)
    {
        Map<String, SolverInput.Task> tasks = input.tasks().stream().collect(
                java.util.stream.Collectors.toMap(SolverInput.Task::taskId, value -> value));
        for (CarryRun run : carry.runs())
        {
            SolverInput.Task firstTask = tasks.get(run.members().get(0).taskId());
            BigDecimal quantity = run.members().stream().map(value -> decimal(value.remainingQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String jobId = stableId("job", input.planVersionId(), "carry", run.executionRunId());
            TaskVars carryVars = carryVarsByRun.get(run.executionRunId());
            Instant jobEnd = carryVars == null ? run.endAt() : axis.instantAt(solver.value(carryVars.end()));
            jobs.add(new PlanCandidate.Job(jobId, "CARRY", firstTask.operationSpecId(), firstTask.workCenterId(),
                    decimal(quantity), firstTask.uomCode(), run.startAt(), jobEnd,
                    run.members().stream().map(SolverInput.ActualOccupancyMember::taskId).sorted().toList(),
                    run.executionRunId()));
            String segmentId = stableId("segment", jobId, run.phase().phaseId(), "1", "1");
            segments.add(new PlanCandidate.Segment(segmentId, jobId, run.phase().phaseId(), run.phase().phaseType(),
                    1, run.startAt(), run.endAt(), decimal(quantity), carryVars == null ? run.endAt() : null,
                    carryVars == null ? decimal(quantity) : null));
            for (SolverInput.ActualOccupancy occupancy : run.occupancies())
                allocations.add(new PlanCandidate.Allocation(stableId("allocation", segmentId,
                        occupancy.sourceRequirementId(), Integer.toString(occupancy.sourceSeatNo()),
                        occupancy.resourceId()), segmentId, occupancy.sourceRequirementId(), occupancy.resourceId(),
                        occupancy.occupiedResourceRole(), occupancy.sourceSeatNo(), occupancy.capacityUsed()));
            if (carryVars == null) continue;
            int segmentNo = 1;
            for (int phaseIndex = 0; phaseIndex < carryVars.phases().size(); phaseIndex++)
            {
                PhaseVars phase = carryVars.phases().get(phaseIndex);
                boolean finalPhase = phaseIndex == carryVars.phases().size() - 1;
                List<SegmentVars> present = phase.segments().stream()
                        .filter(value -> value.present() == null || solver.booleanValue(value.present())).toList();
                for (int physicalIndex = 0; physicalIndex < present.size(); physicalIndex++)
                {
                    SegmentVars physical = present.get(physicalIndex);
                    Instant start = axis.instantAt(solver.value(physical.start()));
                    Instant end = axis.instantAt(solver.value(physical.end()));
                    boolean finalSegment = finalPhase && physicalIndex == present.size() - 1;
                    String futureSegmentId = stableId("segment", jobId, phase.phase().phaseId(),
                            Integer.toString(phase.phase().sequenceNo()), Integer.toString(++segmentNo));
                    segments.add(new PlanCandidate.Segment(futureSegmentId, jobId, phase.phase().phaseId(),
                            phase.phase().phaseType(), segmentNo, start, end, decimal(quantity),
                            finalSegment ? end : null, finalSegment ? decimal(quantity) : null));
                    for (Choice choice : physical.choices())
                        if (solver.booleanValue(choice.selected()))
                            allocations.add(new PlanCandidate.Allocation(stableId("allocation", futureSegmentId,
                                    choice.requirement().requirementId(), Integer.toString(choice.seat()),
                                    choice.resource().resourceId()), futureSegmentId,
                                    choice.requirement().requirementId(), choice.resource().resourceId(),
                                    choice.resource().resourceType(), choice.seat(),
                                    choice.requirement().capacityDemand()));
                }
            }
        }
    }

    private String phaseIdentity(SolverInput.Phase phase)
    {
        return phase.phaseId() + '\u0000' + phase.phaseType() + '\u0000' + phase.sequenceNo();
    }

    private List<TaskVars> uniqueTaskVars(Map<String, TaskVars> tasks)
    {
        return new ArrayList<>(new java.util.LinkedHashSet<>(tasks.values()));
    }

    private List<TaskVars> allTaskVars(Map<String, TaskVars> tasks, Map<String, TaskVars> carryVarsByRun)
    {
        java.util.LinkedHashSet<TaskVars> result = new java.util.LinkedHashSet<>(uniqueTaskVars(tasks));
        result.addAll(carryVarsByRun.values());
        return List.copyOf(result);
    }

    /** 数量在 RUN 阶段按固定前置时间 + 单件节拍连续释放；纯固定周期则整批在阶段末释放。 */
    private int releaseOffsetUnits(SolverInput.Task task, SolverInput.Phase phase, BigDecimal required,
            TimeAxis axis)
    {
        int duration = durationUnits(task, phase, axis);
        BigDecimal secondsPerUnit = decimal(phase.secondsPerUnit());
        if (secondsPerUnit.signum() == 0) return duration;
        BigDecimal seconds = BigDecimal.valueOf(phase.fixedSeconds()).add(secondsPerUnit.multiply(required));
        int offset = axis.ceilDurationSeconds(seconds.setScale(0, RoundingMode.CEILING).longValueExact());
        return Math.max(1, Math.min(duration, offset));
    }

    private long scaled(String value)
    {
        return decimal(value).multiply(BigDecimal.valueOf(CAPACITY_SCALE)).longValueExact();
    }

    private int exactOffset(TimeAxis axis, Instant value)
    {
        if (value == null) throw new IllegalArgumentException("时间锁必须提供起止时间");
        int offset = axis.floorOffset(value);
        if (!axis.instantAt(offset).equals(value)) throw new IllegalArgumentException("锁定时间必须与求解时间单位对齐");
        return offset;
    }

    /**
     * 计划段/分配 ID 与候选落库使用同一稳定算法。可中断阶段的物理段是前缀选择，
     * 因而其本地序号稳定；位于可中断阶段之后的段全局序号取决于求解结果，当前不建立
     * 猜测映射，相关锁会在上方失败关闭。
     */
    private LockTargets indexLockTargets(SolverInput input, Map<String, TaskVars> tasks,
            Map<String, BigDecimal> requiredRelease, MaterialReleaseMath.ReleasePlan materialRelease, TimeAxis axis)
    {
        Map<String, LockableTarget> jobs = new HashMap<>();
        Map<String, LockableTarget> segments = new HashMap<>();
        Map<String, LockableTarget> allocations = new HashMap<>();
        for (TaskVars task : uniqueTaskVars(tasks))
        {
            String jobId = stableId("job", input.planVersionId(), task.stableSourceId());
            jobs.put(jobId, new LockableTarget(task.start(), task.end(), 0, 0, null,
                    task.phases().stream().flatMap(value -> value.choices().stream()).toList(), List.of()));
            int phaseNo = 0;
            int globalSegmentNo = 0;
            boolean deterministicNumber = true;
            for (PhaseVars phase : task.phases())
            {
                phaseNo++;
                if (!deterministicNumber) continue;
                if (phase.phase().interruptible())
                {
                    for (SegmentVars physical : phase.segments())
                    {
                        int candidateSegmentNo = ++globalSegmentNo;
                        String segmentId = stableId("segment", jobId, phase.phase().phaseId(),
                                Integer.toString(phaseNo), Integer.toString(candidateSegmentNo));
                        LockableTarget target = new LockableTarget(physical.start(), physical.end(), 0, 0,
                                physical.present(), physical.choices(), List.of());
                        segments.put(segmentId, target);
                        indexAllocationTargets(allocations, segmentId, target, physical.choices());
                    }
                    deterministicNumber = false;
                    continue;
                }
                List<SegmentSlice> releaseSlices = quantityReleaseSlices(input, task, phase,
                        requiredRelease, materialRelease, axis);
                if (!releaseSlices.isEmpty())
                {
                    for (SegmentSlice slice : releaseSlices)
                    {
                        String segmentId = stableId("segment", jobId, phase.phase().phaseId(),
                                Integer.toString(phaseNo), Integer.toString(++globalSegmentNo));
                        LockableTarget target = new LockableTarget(phase.start(), phase.start(),
                                slice.startOffset(), slice.endOffset(), null, phase.choices(), List.of());
                        segments.put(segmentId, target);
                        indexAllocationTargets(allocations, segmentId, target, phase.choices());
                    }
                    continue;
                }
                String segmentId = stableId("segment", jobId, phase.phase().phaseId(),
                        Integer.toString(phaseNo), Integer.toString(++globalSegmentNo));
                LockableTarget target = new LockableTarget(phase.start(), phase.end(), 0, 0, null,
                        phase.choices(), List.of());
                segments.put(segmentId, target);
                indexAllocationTargets(allocations, segmentId, target, phase.choices());
            }
        }
        return new LockTargets(jobs, segments, allocations);
    }

    private List<SegmentSlice> quantityReleaseSlices(SolverInput input, TaskVars task, PhaseVars phase,
            Map<String, BigDecimal> requiredRelease, MaterialReleaseMath.ReleasePlan materialRelease, TimeAxis axis)
    {
        if (task.sharedBatch() != null || phase != task.productionPhase()) return List.of();
        List<SolverInput.Dependency> outgoing = input.dependencies().stream()
                .filter(value -> value.relationType() == SolverInput.RelationType.QUANTITY)
                .filter(value -> task.memberTaskIds().contains(value.predecessorTaskId())).toList();
        List<SolverInput.MaterialDemand> outgoingMaterial = input.materialDemands().stream()
                .filter(value -> value.sourceTaskId() != null && task.memberTaskIds().contains(value.sourceTaskId()))
                .toList();
        if (outgoing.isEmpty() && outgoingMaterial.isEmpty()) return List.of();
        Set<BigDecimal> milestones = new java.util.TreeSet<>(
                QuantityReleaseMath.releaseMilestones(task.task(), outgoing, requiredRelease));
        milestones.addAll(MaterialReleaseMath.releaseMilestones(task.task(), outgoingMaterial, materialRelease));
        Map<Integer, BigDecimal> cumulativeByOffset = new java.util.TreeMap<>();
        for (BigDecimal milestone : milestones)
        {
            int offset = releaseOffsetUnits(task.task(), phase.phase(), milestone, axis);
            cumulativeByOffset.merge(offset, milestone, BigDecimal::max);
        }
        List<SegmentSlice> result = new ArrayList<>();
        int previousOffset = 0;
        BigDecimal previousQuantity = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : cumulativeByOffset.entrySet())
        {
            if (entry.getKey() > previousOffset)
                result.add(new SegmentSlice(previousOffset, entry.getKey()));
            previousOffset = entry.getKey();
            previousQuantity = entry.getValue();
        }
        int duration = durationUnits(task.task(), phase.phase(), axis);
        if (previousQuantity.compareTo(decimal(task.task().quantity())) < 0 && previousOffset < duration)
            result.add(new SegmentSlice(previousOffset, duration));
        return result;
    }

    private void indexAllocationTargets(Map<String, LockableTarget> allocations, String segmentId,
            LockableTarget segment, List<Choice> choices)
    {
        Map<String, List<Choice>> byIdentity = choices.stream().collect(java.util.stream.Collectors.groupingBy(choice ->
                stableId("allocation", segmentId, choice.requirement().requirementId(),
                        Integer.toString(choice.seat()), choice.resource().resourceId())));
        byIdentity.forEach((allocationId, identityChoices) -> allocations.put(allocationId,
                new LockableTarget(segment.start(), segment.end(), segment.startOffset(), segment.endOffset(),
                        segment.present(), segment.choices(), identityChoices)));
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }
    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private boolean validUuid(String value) { try { UUID.fromString(value); return true; } catch (RuntimeException ignored) { return false; } }
    private String stableId(String... parts) { return UUID.nameUUIDFromBytes(String.join("\u0000", parts)
            .getBytes(StandardCharsets.UTF_8)).toString(); }
    private String memberKey(Set<String> taskIds) { return String.join("\u0000", taskIds.stream().sorted().toList()); }
    private String name(String... parts) { return String.join("_", parts).replace('-', '_'); }

    private record TaskVars(SolverInput.Task task, List<PhaseVars> phases, List<SolverInput.Task> memberTasks,
            SolverInput.SharedBatchCandidate sharedBatch)
    {
        TaskVars
        {
            phases = List.copyOf(phases);
            memberTasks = List.copyOf(memberTasks);
        }
        IntVar start() { return phases.get(0).start(); }
        IntVar end() { return phases.get(phases.size() - 1).end(); }
        String stableSourceId() { return sharedBatch == null ? task.taskId() : sharedBatch.candidateId(); }
        String jobType()
        {
            if (sharedBatch != null) return "SHARED_BATCH";
            return memberTasks.size() == 1
                    && new BigDecimal(memberTasks.get(0).quantity()).compareTo(new BigDecimal(task.quantity())) != 0
                            ? "SPLIT" : "NORMAL";
        }
        Set<String> memberTaskIds()
        {
            return memberTasks.stream().map(SolverInput.Task::taskId).collect(java.util.stream.Collectors.toSet());
        }
        PhaseVars productionPhase()
        {
            for (int index = phases.size() - 1; index >= 0; index--)
                if (phases.get(index).phase().phaseType() == SolverInput.PhaseType.RUN) return phases.get(index);
            return phases.get(phases.size() - 1);
        }
    }
    private record PhaseVars(SolverInput.Task task, SolverInput.Phase phase, IntVar start, IntVar end,
            int duration, List<SegmentVars> segments)
    {
        List<Choice> choices()
        {
            return segments.stream().flatMap(segment -> segment.choices().stream()).toList();
        }
    }
    private record SegmentVars(IntVar start, IntVar end, IntVar duration, BoolVar present, int segmentNo,
            int resumeUnits, List<Choice> choices) { }
    private record Choice(SolverInput.ResourceRequirement requirement, SolverInput.Resource resource,
            SolverInput.AvailabilityWindow window, int seat, BoolVar selected, IntervalVar interval) { }
    private record LockableTarget(IntVar start, IntVar end, int startOffset, int endOffset, BoolVar present,
            List<Choice> choices, List<Choice> identityChoices) { }
    private record LockTargets(Map<String, LockableTarget> jobs, Map<String, LockableTarget> segments,
            Map<String, LockableTarget> allocations) { }
    private record WindowKey(String resourceId, String windowId) { }
    private record DemandInterval(IntervalVar interval, long demand) { }
    private record SegmentDraft(Instant startAt, Instant endAt, String plannedQuantity,
            Instant releaseAt, String releaseQuantity) { }
    private record SegmentSlice(int startOffset, int endOffset) { }
    private record GreedyHint(TaskVars task, PhaseVars phase, Choice choice, int start, int end) { }
    private record CarryProjection(List<CarryRun> runs, Map<String, BigDecimal> quantityByTask)
    {
        Instant fixedReleaseAt(String taskId, BigDecimal required)
        {
            if (required == null || required.signum() <= 0) return null;
            BigDecimal released = BigDecimal.ZERO;
            for (CarryRun run : runs.stream().filter(value -> value.laterPhases().isEmpty())
                    .sorted(Comparator.comparing(CarryRun::endAt)).toList())
            {
                BigDecimal member = run.members().stream().filter(value -> value.taskId().equals(taskId))
                        .map(value -> new BigDecimal(value.remainingQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                released = released.add(member);
                if (released.compareTo(required) >= 0) return run.endAt();
            }
            return null;
        }

        Instant fixedLastReleaseAt(String taskId)
        {
            return runs.stream().filter(run -> run.laterPhases().isEmpty()).filter(run -> run.members().stream()
                    .anyMatch(member -> member.taskId().equals(taskId))).map(CarryRun::endAt)
                    .max(Comparator.naturalOrder()).orElse(null);
        }
    }
    private record CarryRun(String executionRunId, SolverInput.CurrentPhase phase, Instant startAt, Instant endAt,
            List<SolverInput.ActualOccupancyMember> members,
            List<SolverInput.ActualOccupancy> occupancies, List<SolverInput.Phase> laterPhases)
    {
        CarryRun
        {
            members = List.copyOf(members);
            occupancies = List.copyOf(occupancies);
            laterPhases = List.copyOf(laterPhases);
        }
    }
}
