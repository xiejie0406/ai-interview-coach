package com.ruoyi.aps.validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ruoyi.aps.solver.contract.JcsSha256;
import com.ruoyi.aps.solver.contract.MaterialReleaseMath;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.PlanCandidate.Allocation;
import com.ruoyi.aps.solver.contract.PlanCandidate.Job;
import com.ruoyi.aps.solver.contract.PlanCandidate.Segment;
import com.ruoyi.aps.solver.contract.Problem;
import com.ruoyi.aps.solver.contract.Problem.ObjectRef;
import com.ruoyi.aps.solver.contract.Problem.ReasonCode;
import com.ruoyi.aps.solver.contract.Problem.Severity;
import com.ruoyi.aps.solver.contract.Problem.TimeRange;
import com.ruoyi.aps.solver.contract.QuantityReleaseMath;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInput.AvailabilityWindow;
import com.ruoyi.aps.solver.contract.SolverInput.Dependency;
import com.ruoyi.aps.solver.contract.SolverInput.Phase;
import com.ruoyi.aps.solver.contract.SolverInput.Resource;
import com.ruoyi.aps.solver.contract.SolverInput.ResourceRequirement;
import com.ruoyi.aps.solver.contract.SolverInput.Task;
import com.ruoyi.aps.solver.contract.ValidationResult;

/**
 * 与 OR-Tools 无关的确定性硬约束校验器。
 *
 * <p>输入校验回答“是否允许进入求解”；候选校验不信任求解器自报状态，重新检查资格、
 * 席位、日历、NoOverlap/Cumulative、阶段、依赖、批量、锁和修订新鲜度。</p>
 */
public final class IndependentConstraintValidator
{
    public static final String VERSION = "aps-validator-1.0";
    private final JcsSha256 hashes = new JcsSha256();

    public ValidationResult validateInput(SolverInput input, byte[] rawJson, Instant validatedAt)
    {
        List<Problem> problems = new ArrayList<>();
        validateEnvelope(input, rawJson, problems);
        validateInputFacts(input, problems);
        return result(input, null, ValidationResult.Scope.INPUT, validatedAt, false, problems);
    }

    public ValidationResult validateCandidate(SolverInput input, PlanCandidate candidate, String candidateHash,
            long currentDefinitionRevision, long currentExecutionRevision, ValidationResult.Scope scope,
            Instant validatedAt)
    {
        if (scope == ValidationResult.Scope.INPUT) throw new IllegalArgumentException("候选校验不能使用 INPUT 范围");
        List<Problem> problems = new ArrayList<>();
        if (input.definitionRevision() != currentDefinitionRevision
                || input.executionRevision() != currentExecutionRevision)
        {
            problems.add(problem(ReasonCode.STALE_INPUT, "PLAN_VERSION", input.planVersionId(), null,
                    "输入修订已过期", "候选使用的定义或执行修订不再是当前水位", null));
        }
        validateInputFacts(input, problems);
        validateCandidateFacts(input, candidate, problems);
        return result(input, candidateHash, scope, validatedAt,
                scope == ValidationResult.Scope.PUBLISH_PRECHECK, problems);
    }

    private void validateEnvelope(SolverInput input, byte[] rawJson, List<Problem> problems)
    {
        if (!"1.0".equals(input.schemaVersion()) || !"SOLVER_INPUT".equals(input.contractType()))
        {
            problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "SCHEMA", input.requestId(), "schemaVersion",
                    "求解输入版本不兼容", "只接受 SolverInput v1.0，不能降级解析未知版本", null));
        }
        if (input.definitionRevision() < 0 || input.executionRevision() < 0)
        {
            problems.add(problem(ReasonCode.INVALID_REVISION, "PLAN_VERSION", input.planVersionId(), null,
                    "修订号无效", "定义修订和执行修订必须为非负整数", null));
        }
        if (rawJson != null && !Objects.equals(input.inputHash(), hashes.inputHash(rawJson)))
        {
            problems.add(problem(ReasonCode.HASH_MISMATCH, "PLAN_VERSION", input.planVersionId(), "inputHash",
                    "输入摘要不一致", "去除 inputHash 后的 JCS SHA-256 与声明值不一致", null));
        }
    }

    private void validateInputFacts(SolverInput input, List<Problem> problems)
    {
        if (input.horizon() == null || input.horizon().startAt() == null || input.horizon().detailEndAt() == null
                || input.horizon().endAt() == null || input.horizon().startAt().isAfter(input.horizon().detailEndAt())
                || input.horizon().detailEndAt().isAfter(input.horizon().endAt())
                || !input.horizon().endAt().isAfter(input.horizon().startAt()))
        {
            problems.add(problem(ReasonCode.INVALID_INTERVAL, "PLAN_VERSION", input.planVersionId(), "horizon",
                    "计划窗口无效", "horizon 必须满足 start <= detailEnd <= end 且总区间为正", null));
            return;
        }

        Map<String, Resource> resources = unique(input.resources(), Resource::resourceId, "RESOURCE", problems);
        Map<String, Task> tasks = unique(input.tasks(), Task::taskId, "TASK", problems);
        Map<String, List<AvailabilityWindow>> windows = new HashMap<>();
        for (AvailabilityWindow window : input.availabilityWindows())
        {
            if (!resources.containsKey(window.resourceId()))
            {
                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "AVAILABILITY_WINDOW", window.availabilityId(),
                        "resourceId", "日历引用未知资源", "可用窗口必须引用输入中的资源", range(window.startAt(), window.endAt())));
                continue;
            }
            if (!positiveInterval(window.startAt(), window.endAt()) || decimal(window.capacity()).signum() <= 0)
            {
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "AVAILABILITY_WINDOW", window.availabilityId(), null,
                        "可用窗口无效", "资源可用窗口必须是正向半开区间且容量大于零", range(window.startAt(), window.endAt())));
                continue;
            }
            windows.computeIfAbsent(window.resourceId(), ignored -> new ArrayList<>()).add(window);
        }

        for (Task task : tasks.values())
        {
            if (task.workCenterId() == null || task.workCenterId().isBlank())
            {
                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "TASK", task.taskId(), "workCenterId",
                        "任务缺少工作中心", "进入求解的任务必须绑定有效工作中心", null));
            }
            if (task.earliestStartAt() == null || task.earliestStartAt().isBefore(input.horizon().startAt())
                    || !task.earliestStartAt().isBefore(input.horizon().endAt()))
            {
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "TASK", task.taskId(), "earliestStartAt",
                        "任务起点不在计划窗口内", "任务最早开始必须位于 horizon 半开区间内", null));
            }
            if (task.phases().isEmpty())
            {
                problems.add(problem(ReasonCode.MISSING_DURATION, "TASK", task.taskId(), "phases",
                        "任务缺少阶段", "任务至少需要一个可复算阶段", null));
            }
            Set<Integer> sequence = new HashSet<>();
            for (Phase phase : task.phases())
            {
                long duration = durationSeconds(task, phase);
                if (duration <= 0 || !sequence.add(phase.sequenceNo()))
                {
                    problems.add(problem(ReasonCode.MISSING_DURATION, "OPERATION_PHASE", phase.phaseId(), null,
                            "阶段工时不可复算", "阶段序号必须唯一，固定工时与单位工时计算后必须大于零", null));
                }
                boolean invalidSegmentation = phase.segmentResourcePolicy() == null
                        || (phase.interruptible() && (phase.maxSegments() < 2
                                || phase.minSegmentSeconds() <= 0 || phase.minSegmentSeconds() > duration))
                        || (!phase.interruptible() && (phase.maxSegments() != 1
                                || phase.minSegmentSeconds() != 0 || phase.resumeSetupSeconds() != 0));
                if (invalidSegmentation)
                    problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "OPERATION_PHASE", phase.phaseId(),
                            "maxSegments", "阶段分段规则不完整",
                            "可中断阶段必须提供至少两段、正数最小分段和资源连续性；不可中断阶段不能携带分段参数", null));
                for (ResourceRequirement requirement : phase.requirements())
                {
                    if (requirement.holdOnPause() && (!phase.interruptible()
                            || phase.segmentResourcePolicy() != SolverInput.SegmentResourcePolicy.SAME_RESOURCES))
                        problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "RESOURCE_REQUIREMENT",
                                requirement.requirementId(), "holdOnPause", "暂停资源保留规则无效",
                                "暂停保留只允许用于 SAME_RESOURCES 的可中断阶段", null));
                    List<Resource> eligible = requirement.candidateResourceIds().stream().distinct()
                            .map(resources::get).filter(Objects::nonNull)
                            .filter(resource -> eligible(resource, requirement, task.earliestStartAt())).toList();
                    if (eligible.size() < requirement.seatCount())
                    {
                        problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "RESOURCE_REQUIREMENT",
                                requirement.requirementId(), "candidateResourceIds", "合格候选资源不足",
                                "去重并复核类型、中心、容量和技能后，候选数少于必需席位数", null));
                    }
                    else if (!(phase.interruptible()
                            ? hasSegmentableCapacity(eligible, windows, requirement, task.earliestStartAt(),
                                    input.horizon().endAt(), duration, phase.maxSegments())
                            : hasCommonWindow(eligible, windows, requirement, task.earliestStartAt(),
                                    input.horizon().endAt(), duration)))
                    {
                        problems.add(problem(ReasonCode.NO_COMMON_WINDOW, "RESOURCE_REQUIREMENT",
                                requirement.requirementId(), "candidateResourceIds", "候选资源没有共同可用窗口",
                                "必需席位在计划窗口内没有覆盖标准阶段时长的共同净可用区间", null));
                    }
                }
            }
        }

        validateBaselineInput(input, tasks, resources, problems);
        validateLockInput(input, resources, problems);

        for (Dependency dependency : input.dependencies())
        {
            Task predecessor = tasks.get(dependency.predecessorTaskId());
            Task successor = tasks.get(dependency.successorTaskId());
            if (predecessor == null || successor == null)
            {
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "DEPENDENCY", dependency.dependencyId(),
                        null, "依赖引用未知任务", "前置和后继任务必须都存在于输入中", null));
                continue;
            }
            if (dependency.relationType() == SolverInput.RelationType.SAME_START)
            {
                problems.add(problem(ReasonCode.UNSUPPORTED_SYNC_RULE, "DEPENDENCY", dependency.dependencyId(),
                        "relationType", "同步开始规则暂不支持执行", "SAME_START 来源被保留，但 P0 必须在求解前阻断", null));
            }
            if (dependency.relationType() == SolverInput.RelationType.QUANTITY)
            {
                boolean qty = dependency.thresholdQty() != null;
                boolean ratio = dependency.thresholdRatio() != null;
                if (qty == ratio || (qty && decimal(dependency.thresholdQty()).compareTo(decimal(predecessor.quantity())) > 0)
                        || (qty && !Objects.equals(dependency.uomCode(), predecessor.uomCode())))
                {
                    problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "DEPENDENCY", dependency.dependencyId(),
                            "thresholdQty", "数量释放关系无效", "门槛必须二选一、不得超过前置任务数量且数量单位必须一致", null));
                }
            }
        }

        try
        {
            QuantityReleaseMath.requiredReleaseByDependency(input.dependencies(), tasks);
        }
        catch (IllegalArgumentException exception)
        {
            problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "PLAN_VERSION", input.planVersionId(),
                    "dependencies", "有限产出不足", exception.getMessage(), null));
        }

        try { MaterialReleaseMath.plan(input); }
        catch (IllegalArgumentException exception)
        {
            problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "PLAN_VERSION", input.planVersionId(),
                    "materialDemands", "物料有限供给或质量放行无效", exception.getMessage(), null));
        }

        Set<String> occupancyIds = new HashSet<>();
        Map<String, List<SolverInput.ActualOccupancyMember>> membersByRun = new HashMap<>();
        Map<String, String> phaseByRun = new HashMap<>();
        Map<String, Instant> releaseByRun = new HashMap<>();
        input.actualOccupancies().forEach(value -> {
            if (!occupancyIds.add(value.occupancyId()))
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "ACTUAL_OCCUPANCY",
                        value.occupancyId(), "occupancyId", "实际物理占用重复",
                        "同一 M26 物理占用只能进入求解输入一次，不能按共享批成员复制", null));
            if (value.startAt() == null || (value.endAt() == null
                    && value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED && value.releaseAt() == null))
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "ACTUAL_OCCUPANCY", value.occupancyId(),
                        "releaseAt", "实际占用区间不完整", "可信开放占用必须提供 releaseAt，所有占用必须提供 startAt", null));
            Set<String> memberTaskIds = new HashSet<>();
            boolean invalidMember = value.members().stream().anyMatch(member -> !memberTaskIds.add(member.taskId())
                    || !tasks.containsKey(member.taskId()) || !tasks.get(member.taskId()).uomCode().equals(member.uomCode())
                    || decimal(member.remainingQuantity()).signum() <= 0);
            if (invalidMember || value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                    && value.remainingQuantity() != null && value.members().isEmpty())
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "ACTUAL_OCCUPANCY",
                        value.occupancyId(), "members", "实际占用成员无效",
                        "可信 carry 必须列出不重不漏的任务成员、正剩余量和一致单位", null));
            if (value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED)
            {
                List<SolverInput.ActualOccupancyMember> normalized = value.members().stream()
                        .sorted(Comparator.comparing(SolverInput.ActualOccupancyMember::taskId)).toList();
                List<SolverInput.ActualOccupancyMember> prior = normalized.isEmpty() ? null
                        : membersByRun.putIfAbsent(value.executionRunId(), normalized);
                if (!normalized.isEmpty() && prior != null && !prior.equals(normalized))
                    problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "ACTUAL_OCCUPANCY",
                            value.occupancyId(), "members", "同一 run 的 carry 成员不一致",
                            "一个执行 run 的不同物理资源必须引用同一组剩余成员数量", null));
                boolean carryFactsMissing = !normalized.isEmpty() && (value.endAt() != null
                        || value.currentPhase() == null || value.releaseAt() == null
                        || !value.releaseAt().isAfter(input.horizon().planningAnchorAt())
                        || value.sourceRequirementId() == null || value.sourceSeatNo() == null
                        || value.capacityUsed() == null || decimal(value.capacityUsed()).signum() <= 0
                        || normalized.stream().anyMatch(member -> {
                            Task task = tasks.get(member.taskId());
                            return task == null || !task.operationSpecId().equals(value.currentPhase().operationSpecId())
                                    || task.phases().stream().noneMatch(phase ->
                                            phase.phaseId().equals(value.currentPhase().phaseId())
                                                    && phase.phaseType() == value.currentPhase().phaseType());
                        }));
                String phaseIdentity = value.currentPhase() == null ? null
                        : value.currentPhase().operationSpecId() + "\u0000" + value.currentPhase().phaseId()
                                + "\u0000" + value.currentPhase().phaseType();
                String priorPhase = phaseIdentity == null ? null
                        : phaseByRun.putIfAbsent(value.executionRunId(), phaseIdentity);
                Instant priorRelease = value.releaseAt() == null ? null
                        : releaseByRun.putIfAbsent(value.executionRunId(), value.releaseAt());
                if (carryFactsMissing || priorPhase != null && !priorPhase.equals(phaseIdentity)
                        || priorRelease != null && !priorRelease.equals(value.releaseAt()))
                    problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "ACTUAL_OCCUPANCY",
                            value.occupancyId(), "currentPhase", "可信 carry 固定事实不完整",
                            "同一 run 必须给出一致的当前阶段、预计释放、来源资源需求、席位和容量；释放必须晚于 planningAnchorAt",
                            null));
                if (value.taskId() != null && !normalized.isEmpty() && (normalized.size() != 1
                        || !value.taskId().equals(normalized.get(0).taskId())
                        || value.remainingQuantity() == null
                        || decimal(value.remainingQuantity()).compareTo(
                                decimal(normalized.get(0).remainingQuantity())) != 0))
                    problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "ACTUAL_OCCUPANCY",
                            value.occupancyId(), "taskId", "单任务兼容字段与成员不一致",
                            "taskId/remainingQuantity 仅是单成员兼容投影，必须与 members 唯一成员一致", null));
            }
            if (value.releaseConfidence() == SolverInput.ReleaseConfidence.UNKNOWN)
                problems.add(problem(ReasonCode.OPEN_OCCUPANCY_UNKNOWN_RELEASE, "ACTUAL_OCCUPANCY",
                        value.occupancyId(), "releaseAt", "开放占用缺少可信释放时间",
                        "未知释放时间必须保守阻塞到 horizon 结束，不能猜测剩余工时", null));
        });

        Set<String> fixedBatchMembers = new HashSet<>();
        input.sharedBatchCandidates().forEach(batch -> {
            BigDecimal sum = batch.members().stream().map(member -> decimal(member.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
            Set<String> members = batch.members().stream().map(SolverInput.SharedBatchMember::taskId).collect(Collectors.toSet());
            boolean unitMismatch = batch.members().stream().anyMatch(member -> !Objects.equals(member.uomCode(), batch.capacityUomCode()));
            Task referenceTask = batch.members().isEmpty() ? null : tasks.get(batch.members().get(0).taskId());
            boolean taskMismatch = batch.members().stream().anyMatch(member -> {
                Task task = tasks.get(member.taskId());
                return task == null || !task.operationSpecId().equals(batch.operationSpecId())
                        || !task.workCenterId().equals(batch.workCenterId())
                        || !task.uomCode().equals(member.uomCode())
                        || decimal(member.quantity()).compareTo(decimal(task.quantity())) != 0
                        || task.phases().stream().filter(phase -> phase.phaseType() == SolverInput.PhaseType.RUN).count() != 1
                        || referenceTask == null || !Objects.equals(task.phases(), referenceTask.phases());
            });
            boolean duplicateFixedMember = "MANUAL_FIXED".equals(batch.sourceType())
                    && members.stream().anyMatch(member -> !fixedBatchMembers.add(member));
            if (batch.members().size() < 2 || members.size() != batch.members().size()
                    || sum.compareTo(decimal(batch.capacity())) > 0 || unitMismatch || taskMismatch
                    || duplicateFixedMember || batch.cycleDurationSeconds() <= 0
                    || batch.compatibilityKey() == null || batch.compatibilityKey().isBlank())
                problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "OPERATION_SPEC", batch.operationSpecId(), null,
                        "共享批候选不兼容", "共享批成员必须唯一、同工序同中心同单位、阶段快照一致且完整覆盖任务量，并且不超批容量；固定成员不能重复归属", null));
        });
    }

    private void validateCandidateFacts(SolverInput input, PlanCandidate candidate, List<Problem> problems)
    {
        Map<String, Task> tasks = input.tasks().stream().collect(Collectors.toMap(Task::taskId, Function.identity()));
        Map<String, Resource> resources = input.resources().stream().collect(Collectors.toMap(Resource::resourceId, Function.identity()));
        Map<String, List<AvailabilityWindow>> windows = input.availabilityWindows().stream()
                .collect(Collectors.groupingBy(AvailabilityWindow::resourceId));
        Map<String, Job> jobs = unique(candidate.jobs(), Job::jobId, "PLAN_JOB", problems);
        Map<String, Segment> segments = unique(candidate.segments(), Segment::segmentId, "PLAN_SEGMENT", problems);
        Map<String, List<Segment>> segmentsByJob = candidate.segments().stream().collect(Collectors.groupingBy(Segment::jobId));
        Map<String, List<Allocation>> allocationsBySegment = candidate.allocations().stream()
                .collect(Collectors.groupingBy(Allocation::segmentId));
        Map<String, SolverInput.SharedBatchCandidate> sharedByMembers = input.sharedBatchCandidates().stream()
                .collect(Collectors.toMap(value -> memberKey(value.members().stream()
                        .map(SolverInput.SharedBatchMember::taskId).toList()), Function.identity(), (left, right) -> left));
        Map<String, Job> jobByTask = new HashMap<>();
        Map<String, List<Job>> jobsByTask = new HashMap<>();
        Map<String, List<SolverInput.ActualOccupancy>> occupanciesByRun = input.actualOccupancies().stream()
                .filter(value -> value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                        && value.endAt() == null && !value.members().isEmpty())
                .collect(Collectors.groupingBy(SolverInput.ActualOccupancy::executionRunId));
        for (Job job : jobs.values())
        {
            if (!positiveInterval(job.startAt(), job.endAt()) || job.startAt().isBefore(candidate.horizonStartAt())
                    || job.endAt().isAfter(candidate.horizonEndAt()))
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "PLAN_JOB", job.jobId(), null,
                        "计划作业区间无效", "作业必须位于候选 horizon 内且结束晚于开始", range(job.startAt(), job.endAt())));
            for (String taskId : job.memberTaskIds())
            {
                if (!tasks.containsKey(taskId))
                    problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_JOB", job.jobId(), "memberTaskIds",
                            "作业成员任务无效", "作业成员必须引用当前输入任务", null));
                else
                {
                    jobsByTask.computeIfAbsent(taskId, ignored -> new ArrayList<>()).add(job);
                    if (!"CARRY".equals(job.jobType())) jobByTask.put(taskId, job);
                    else jobByTask.putIfAbsent(taskId, job);
                }
            }
            List<Task> members = job.memberTaskIds().stream().map(tasks::get).filter(Objects::nonNull).toList();
            boolean identityMismatch = members.isEmpty() || members.stream().anyMatch(task ->
                    !task.operationSpecId().equals(job.operationSpecId())
                            || !task.workCenterId().equals(job.workCenterId())
                            || !task.uomCode().equals(job.uomCode()));
            if ("CARRY".equals(job.jobType()))
            {
                List<SolverInput.ActualOccupancy> run = job.carryRunId() == null ? List.of()
                        : occupanciesByRun.getOrDefault(job.carryRunId(), List.of());
                List<SolverInput.ActualOccupancyMember> carryMembers = run.isEmpty() ? List.of()
                        : run.get(0).members();
                BigDecimal expected = carryMembers.stream().map(value -> decimal(value.remainingQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                Set<String> expectedMembers = carryMembers.stream()
                        .map(SolverInput.ActualOccupancyMember::taskId).collect(Collectors.toSet());
                if (run.isEmpty() || identityMismatch || !expectedMembers.equals(Set.copyOf(job.memberTaskIds()))
                        || expected.compareTo(decimal(job.plannedQuantity())) != 0)
                    problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_JOB", job.jobId(), "carryRunId",
                            "carry 作业与活动 run 不一致",
                            "carryRunId、成员、工序、中心、单位和成员剩余量必须精确匹配可信开放占用", null));
            }
            else if (job.carryRunId() != null)
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "PLAN_JOB", job.jobId(), "carryRunId",
                        "普通候选错误引用执行 run", "只有 CARRY 作业可以携带 carryRunId", null));
            else if ("SHARED_BATCH".equals(job.jobType()))
            {
                SolverInput.SharedBatchCandidate batch = sharedByMembers.get(memberKey(job.memberTaskIds()));
                BigDecimal expected = batch == null ? BigDecimal.ZERO : batch.members().stream()
                        .map(value -> decimal(value.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (batch == null || identityMismatch || !batch.operationSpecId().equals(job.operationSpecId())
                        || !batch.workCenterId().equals(job.workCenterId())
                        || expected.compareTo(decimal(job.plannedQuantity())) != 0)
                    problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_JOB", job.jobId(), null,
                            "共享作业与批准候选不一致", "共享作业成员、工序、中心、单位和成员数量总和必须精确匹配输入候选", null));
            }
            else if (members.size() != 1 || identityMismatch
                    || decimal(job.plannedQuantity()).compareTo(decimal(members.get(0).quantity())) > 0)
                problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_JOB", job.jobId(), null,
                        "普通作业数量归属无效", "普通/拆分作业必须只有一个任务成员且作业量不能超过任务待排量", null));
        }

        for (Map.Entry<String, List<Job>> entry : jobsByTask.entrySet())
        {
            long carries = entry.getValue().stream().filter(job -> "CARRY".equals(job.jobType())).count();
            long futureJobs = entry.getValue().size() - carries;
            BigDecimal covered = entry.getValue().stream().map(job -> memberQuantity(job, entry.getKey(),
                    sharedByMembers, occupanciesByRun)).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (carries > 1 || futureJobs > 1 || covered.compareTo(decimal(tasks.get(entry.getKey()).quantity())) != 0)
                problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "TASK", entry.getKey(), "quantity",
                        "任务待排量覆盖不守恒",
                        "每个任务最多一个活动 carry 和一个未来作业，二者成员量之和必须精确等于任务待排量", null));
        }

        Set<String> future = new java.util.HashSet<>(candidate.futureCarryForwardTaskIds());
        Set<String> unplanned = new java.util.HashSet<>(candidate.unplannedTaskIds());
        if (future.size() != candidate.futureCarryForwardTaskIds().size()
                || unplanned.size() != candidate.unplannedTaskIds().size())
            problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_VERSION", input.planVersionId(), null,
                    "候选任务清单存在重复", "futureCarryForwardTaskIds 与 unplannedTaskIds 内部必须去重", null));
        for (Task task : tasks.values())
        {
            if (task.planningClass() == SolverInput.PlanningClass.MANDATORY_DETAIL
                    && !jobByTask.containsKey(task.taskId()) && !unplanned.contains(task.taskId()))
                problems.add(problem(ReasonCode.NO_COMMON_WINDOW, "TASK", task.taskId(), null,
                        "必排任务未进入候选", "MANDATORY_DETAIL 任务必须形成作业或显式列入未排任务", null));
            if (task.planningClass() == SolverInput.PlanningClass.FUTURE_CARRY_FORWARD
                    && (!future.contains(task.taskId()) || jobByTask.containsKey(task.taskId())))
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "TASK", task.taskId(), null,
                        "远期任务边界错误", "FUTURE_CARRY_FORWARD 任务不生成详细作业且必须进入远期结转清单", null));
        }
        if (future.stream().anyMatch(id -> !tasks.containsKey(id)
                || tasks.get(id).planningClass() != SolverInput.PlanningClass.FUTURE_CARRY_FORWARD)
                || unplanned.stream().anyMatch(id -> !tasks.containsKey(id) || jobByTask.containsKey(id)))
            problems.add(problem(ReasonCode.BATCH_INCOMPATIBLE, "PLAN_VERSION", input.planVersionId(), null,
                    "候选任务清单引用无效", "结转/未排清单只能引用输入任务，且已排任务不能同时标记为未排", null));

        for (Segment segment : segments.values())
        {
            Job job = jobs.get(segment.jobId());
            if (job == null || !positiveInterval(segment.startAt(), segment.endAt())
                    || (job != null && (segment.startAt().isBefore(job.startAt()) || segment.endAt().isAfter(job.endAt()))))
            {
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "PLAN_SEGMENT", segment.segmentId(), null,
                        "计划分段区间无效", "分段必须位于所属作业区间内且结束晚于开始", range(segment.startAt(), segment.endAt())));
            }
        }

        for (Allocation allocation : candidate.allocations())
        {
            Segment segment = segments.get(allocation.segmentId());
            Resource resource = resources.get(allocation.resourceId());
            ResourceRequirement requirement = findRequirement(tasks, jobs.get(segment == null ? null : segment.jobId()),
                    segment == null ? null : segment.phaseId(), allocation.requirementId());
            if (segment == null || resource == null || requirement == null
                    || allocation.resourceType() != resource.resourceType()
                    || !requirement.candidateResourceIds().contains(resource.resourceId())
                    || !eligible(resource, requirement, segment.startAt()))
            {
                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "PLAN_ALLOCATION", allocation.allocationId(),
                        "resourceId", "计划分配使用了不合格资源", "资源必须属于该要求的候选集并在执行时点满足类型、中心、容量和技能", segment == null ? null : range(segment.startAt(), segment.endAt())));
                continue;
            }
            boolean covered = windows.getOrDefault(resource.resourceId(), List.of()).stream()
                    .anyMatch(window -> !segment.startAt().isBefore(window.startAt()) && !segment.endAt().isAfter(window.endAt())
                            && decimal(allocation.capacityUsed()).compareTo(decimal(window.capacity())) <= 0);
            if (!covered)
                problems.add(problem(ReasonCode.NO_COMMON_WINDOW, "PLAN_ALLOCATION", allocation.allocationId(),
                        "segmentId", "计划分配超出资源可用日历", "完整分段必须由同一个净可用窗口覆盖且容量足够", range(segment.startAt(), segment.endAt())));
        }

        for (Job job : jobs.values().stream().filter(value -> "CARRY".equals(value.jobType())).toList())
        {
            List<SolverInput.ActualOccupancy> runOccupancies = occupanciesByRun.getOrDefault(job.carryRunId(), List.of());
            SolverInput.ActualOccupancy first = runOccupancies.stream().findFirst().orElse(null);
            Set<String> fixedSegmentIds = first == null || first.currentPhase() == null ? Set.of()
                    : segmentsByJob.getOrDefault(job.jobId(), List.of()).stream()
                            .filter(segment -> segment.phaseId().equals(first.currentPhase().phaseId())
                                    && segment.phaseType() == first.currentPhase().phaseType()
                                    && segment.startAt().equals(input.horizon().planningAnchorAt())
                                    && segment.endAt().equals(first.releaseAt()))
                            .map(Segment::segmentId).collect(Collectors.toSet());
            Set<String> expected = runOccupancies.stream().map(value -> value.sourceRequirementId() + "\u0000"
                    + value.sourceSeatNo() + "\u0000" + value.resourceId() + "\u0000" + value.capacityUsed())
                    .collect(Collectors.toSet());
            Set<String> actual = fixedSegmentIds.stream()
                    .flatMap(segmentId -> allocationsBySegment.getOrDefault(segmentId, List.of()).stream())
                    .map(value -> value.requirementId() + "\u0000" + value.seatNo() + "\u0000"
                            + value.resourceId() + "\u0000" + value.capacityUsed())
                    .collect(Collectors.toSet());
            if (fixedSegmentIds.size() != 1 || expected.size() != runOccupancies.size() || !expected.equals(actual))
                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "PLAN_JOB", job.jobId(), "carryRunId",
                        "carry 资源固定不完整",
                        "carry 当前固定段必须逐项保留活动 M26 的来源需求、席位、具名资源和容量", null));
        }

        validateSeats(candidate, segments, allocationsBySegment, tasks, jobs, problems);
        validateSegmentPolicies(input, candidate, jobs, segmentsByJob, allocationsBySegment, tasks, problems);
        validateResourceLoads(input, candidate, segments, resources, problems);
        validatePhaseDurations(input, jobs, segmentsByJob, tasks, sharedByMembers, problems);
        validateReleaseConservation(input, jobs, jobsByTask, segmentsByJob, problems);
        validateDependencies(input, jobsByTask, segmentsByJob, problems);
        validateFrozenBaseline(input, jobs, candidate, problems);
        validateLocks(input, jobs, candidate, problems);
    }

    private void validateBaselineInput(SolverInput input, Map<String, Task> tasks,
            Map<String, Resource> resources, List<Problem> problems)
    {
        SolverInput.BaseVersion base = input.baseVersion();
        if (base == null) return;
        if (base.freezeEndAt() == null || input.horizon().planningAnchorAt() == null
                || base.freezeEndAt() != null
                        && base.freezeEndAt().isBefore(input.horizon().planningAnchorAt())
                || base.freezeEndAt().isAfter(input.horizon().endAt()) || base.jobs().isEmpty())
            problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "PLAN_VERSION", base.planVersionId(),
                    "freezeEndAt", "基线冻结窗口无效",
                    "重排基线必须提供位于 planningAnchorAt 与 horizon.endAt 之间的冻结截止时间和至少一个基线作业", null));
        Set<String> baselineIds = new HashSet<>(), assignedTasks = new HashSet<>();
        Set<String> sharedMemberKeys = input.sharedBatchCandidates().stream()
                .map(value -> memberKey(value.members().stream().map(SolverInput.SharedBatchMember::taskId).toList()))
                .collect(Collectors.toSet());
        for (SolverInput.BaselineJob job : base.jobs())
        {
            boolean membersValid = !job.memberTaskIds().isEmpty()
                    && job.memberTaskIds().stream().allMatch(tasks::containsKey)
                    && job.memberTaskIds().stream().allMatch(assignedTasks::add)
                    && (job.memberTaskIds().size() == 1 || sharedMemberKeys.contains(memberKey(job.memberTaskIds())));
            if (!baselineIds.add(job.baselineJobId()) || !membersValid)
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, "PLAN_JOB", job.baselineJobId(),
                        "memberTaskIds", "基线作业映射无效",
                        "基线作业 ID 和成员归属必须唯一；多成员作业必须精确匹配当前固定共享批", null));
            if (!positiveInterval(job.startAt(), job.endAt())
                    || job.startAt().isBefore(input.horizon().startAt())
                    || job.endAt().isAfter(input.horizon().endAt()))
                problems.add(problem(ReasonCode.INVALID_INTERVAL, "PLAN_JOB", job.baselineJobId(), null,
                        "基线作业区间无效", "基线作业必须完整位于当前 horizon 内", range(job.startAt(), job.endAt())));
            if (job.resourceIds().stream().anyMatch(id -> !resources.containsKey(id)))
                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "PLAN_JOB", job.baselineJobId(),
                        "resourceIds", "基线作业引用未知资源", "基线资源必须存在于当前求解输入资源集合", null));
        }
    }

    private void validateFrozenBaseline(SolverInput input, Map<String, Job> jobs,
            PlanCandidate candidate, List<Problem> problems)
    {
        SolverInput.BaseVersion base = input.baseVersion();
        if (base == null || base.freezeEndAt() == null) return;
        for (SolverInput.BaselineJob baseline : base.jobs().stream()
                .filter(value -> value.startAt().isBefore(base.freezeEndAt())).toList())
        {
            Job job = jobs.values().stream().filter(value -> memberKey(value.memberTaskIds())
                    .equals(memberKey(baseline.memberTaskIds()))).findFirst().orElse(null);
            Set<String> actualResources = job == null ? Set.of() : candidate.allocations().stream()
                    .filter(value -> candidate.segments().stream().anyMatch(segment ->
                            segment.segmentId().equals(value.segmentId()) && segment.jobId().equals(job.jobId())))
                    .map(Allocation::resourceId).collect(Collectors.toSet());
            if (job == null || !Objects.equals(job.startAt(), baseline.startAt())
                    || !Objects.equals(job.endAt(), baseline.endAt())
                    || !actualResources.equals(Set.copyOf(baseline.resourceIds())))
                problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_JOB", baseline.baselineJobId(), null,
                        "冻结区基线作业发生变化",
                        "基线开始早于 freezeEndAt 的作业必须保持成员、起止和具体资源完全一致",
                        range(baseline.startAt(), baseline.endAt())));
        }
    }

    private void validateLockInput(SolverInput input, Map<String, Resource> resources, List<Problem> problems)
    {
        Set<String> lockIds = new HashSet<>();
        for (SolverInput.PlanLock lock : input.locks())
        {
            boolean knownTarget = Set.of("JOB", "SEGMENT", "ALLOCATION").contains(lock.targetType());
            boolean knownType = Set.of("TIME", "RESOURCE", "FULL").contains(lock.lockType());
            boolean hasTime = positiveInterval(lock.lockedStartAt(), lock.lockedEndAt());
            boolean hasResources = lock.lockedResourceIds() != null && !lock.lockedResourceIds().isEmpty()
                    && lock.lockedResourceIds().stream().allMatch(resources::containsKey);
            boolean validValue = "TIME".equals(lock.lockType()) ? hasTime && lock.lockedResourceIds().isEmpty()
                    : "RESOURCE".equals(lock.lockType()) ? lock.lockedStartAt() == null
                            && lock.lockedEndAt() == null && hasResources
                    : "FULL".equals(lock.lockType()) && hasTime && hasResources;
            if (!lockIds.add(lock.lockId()) || !knownTarget || !knownType || !validValue)
                problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), null,
                        "计划锁结构或引用无效",
                        "锁 ID 必须唯一；目标/类型、时间区间和锁定资源必须符合 TIME、RESOURCE 或 FULL 口径", null));
        }
    }

    private void validateSeats(PlanCandidate candidate, Map<String, Segment> segments,
            Map<String, List<Allocation>> bySegment, Map<String, Task> tasks, Map<String, Job> jobs,
            List<Problem> problems)
    {
        for (Segment segment : segments.values())
        {
            Job job = jobs.get(segment.jobId());
            if (job == null) continue;
            Task task = job.memberTaskIds().stream().map(tasks::get).filter(Objects::nonNull).findFirst().orElse(null);
            if (task == null) continue;
            Phase phase = task.phases().stream().filter(value -> value.phaseId().equals(segment.phaseId())).findFirst().orElse(null);
            if (phase == null) continue;
            for (ResourceRequirement requirement : phase.requirements())
            {
                List<Allocation> values = bySegment.getOrDefault(segment.segmentId(), List.of()).stream()
                        .filter(value -> value.requirementId().equals(requirement.requirementId())).toList();
                long distinctSeats = values.stream().map(Allocation::seatNo).distinct().count();
                long distinctResources = values.stream().map(Allocation::resourceId).distinct().count();
                if (values.size() != requirement.seatCount() || distinctSeats != requirement.seatCount()
                        || distinctResources != requirement.seatCount())
                    problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "RESOURCE_REQUIREMENT",
                            requirement.requirementId(), "seatCount", "必需协作席位未被不同资源完整填充",
                            "每个席位必须恰有一个分配，同一资源不能同时填充两个必需席位", range(segment.startAt(), segment.endAt())));
            }
        }
    }

    private void validateSegmentPolicies(SolverInput input, PlanCandidate candidate, Map<String, Job> jobs,
            Map<String, List<Segment>> segmentsByJob, Map<String, List<Allocation>> allocationsBySegment,
            Map<String, Task> tasks, List<Problem> problems)
    {
        Map<String, Segment> segmentById = candidate.segments().stream().collect(
                Collectors.toMap(Segment::segmentId, Function.identity(), (left, right) -> left));
        for (Job job : jobs.values())
        {
            Task task = job.memberTaskIds().stream().map(tasks::get).filter(Objects::nonNull).findFirst().orElse(null);
            if (task == null) continue;
            for (Phase phase : task.phases().stream().filter(Phase::interruptible).toList())
            {
                List<Segment> phaseSegments = segmentsByJob.getOrDefault(job.jobId(), List.of()).stream()
                        .filter(segment -> segment.phaseId().equals(phase.phaseId()))
                        .sorted(Comparator.comparing(Segment::startAt)).toList();
                if (phaseSegments.isEmpty()) continue;
                for (ResourceRequirement requirement : phase.requirements())
                {
                    Map<Integer, String> stableResourceBySeat = new HashMap<>();
                    for (Segment segment : phaseSegments)
                        for (Allocation allocation : allocationsBySegment.getOrDefault(segment.segmentId(), List.of()).stream()
                                .filter(value -> value.requirementId().equals(requirement.requirementId())).toList())
                        {
                            String previous = stableResourceBySeat.putIfAbsent(allocation.seatNo(), allocation.resourceId());
                            if (phase.segmentResourcePolicy() == SolverInput.SegmentResourcePolicy.SAME_RESOURCES
                                    && previous != null && !previous.equals(allocation.resourceId()))
                                problems.add(problem(ReasonCode.NO_QUALIFIED_RESOURCE, "PLAN_ALLOCATION",
                                        allocation.allocationId(), "resourceId", "可中断阶段更换了固定资源",
                                        "SAME_RESOURCES 要求每个席位跨全部物理分段使用同一资源", null));
                        }
                    if (!requirement.holdOnPause() || physicalRuns(phaseSegments) <= 1) continue;
                    Instant holdStart = phaseSegments.get(0).startAt();
                    Instant holdEnd = phaseSegments.get(phaseSegments.size() - 1).endAt();
                    for (String resourceId : stableResourceBySeat.values())
                    {
                        boolean planConflict = candidate.allocations().stream()
                                .filter(allocation -> allocation.resourceId().equals(resourceId))
                                .map(allocation -> segmentById.get(allocation.segmentId())).filter(Objects::nonNull)
                                .anyMatch(segment -> !segment.jobId().equals(job.jobId())
                                        && overlaps(segment.startAt(), segment.endAt(), holdStart, holdEnd));
                        boolean actualConflict = input.actualOccupancies().stream()
                                .filter(occupancy -> occupancy.resourceId().equals(resourceId))
                                .filter(occupancy -> occupancy.startAt() != null)
                                .filter(occupancy -> occupancy.endAt() != null
                                        || occupancy.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                                                && occupancy.releaseAt() != null
                                        || occupancy.releaseConfidence() != SolverInput.ReleaseConfidence.TRUSTED
                                                && input.horizon().endAt() != null)
                                .anyMatch(occupancy -> overlaps(occupancy.startAt(),
                                        occupancy.endAt() != null ? occupancy.endAt()
                                                : occupancy.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                                                        ? occupancy.releaseAt() : input.horizon().endAt(),
                                        holdStart, holdEnd));
                        if (planConflict || actualConflict)
                            problems.add(problem(ReasonCode.RESOURCE_OVERLAP, "RESOURCE", resourceId, null,
                                    "暂停保留资源期间发生占用冲突",
                                    "holdOnPause 要求资源从首段开始到末段结束持续保留，暂停空档不能分配给其他作业",
                                    range(holdStart, holdEnd)));
                    }
                }
            }
        }
    }

    private void validateResourceLoads(SolverInput input, PlanCandidate candidate, Map<String, Segment> segments,
            Map<String, Resource> resources, List<Problem> problems)
    {
        Map<String, Job> jobs = candidate.jobs().stream().collect(Collectors.toMap(Job::jobId, value -> value));
        Map<String, List<Allocation>> byResource = candidate.allocations().stream().collect(Collectors.groupingBy(Allocation::resourceId));
        byResource.forEach((resourceId, allocations) -> {
            Resource resource = resources.get(resourceId);
            if (resource == null) return;
            List<Allocation> sorted = allocations.stream().filter(value -> segments.containsKey(value.segmentId()))
                    .sorted(Comparator.comparing(value -> segments.get(value.segmentId()).startAt())).toList();
            for (int i = 0; i < sorted.size(); i++)
            {
                Segment left = segments.get(sorted.get(i).segmentId());
                BigDecimal concurrent = decimal(sorted.get(i).capacityUsed());
                for (int j = i + 1; j < sorted.size(); j++)
                {
                    Segment right = segments.get(sorted.get(j).segmentId());
                    if (!right.startAt().isBefore(left.endAt())) break;
                    if (left.startAt().isBefore(right.endAt()))
                    {
                        if (resource.exclusive())
                            problems.add(problem(ReasonCode.RESOURCE_OVERLAP, "RESOURCE", resourceId, null,
                                    "互斥资源发生重叠", "同一互斥资源不能同时分配给重叠计划段", range(right.startAt(), min(left.endAt(), right.endAt()))));
                        concurrent = concurrent.add(decimal(sorted.get(j).capacityUsed()));
                    }
                }
                if (!resource.exclusive() && concurrent.compareTo(decimal(resource.capacity())) > 0)
                    problems.add(problem(ReasonCode.CAPACITY_EXCEEDED, "RESOURCE", resourceId, null,
                            "容量资源超量", "重叠计划段的容量用量合计超过资源容量", range(left.startAt(), left.endAt())));
            }
        });

        for (SolverInput.ActualOccupancy occupancy : input.actualOccupancies())
        {
            Instant occupiedEnd = occupancy.endAt() != null ? occupancy.endAt()
                    : occupancy.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                            ? occupancy.releaseAt() : input.horizon().endAt();
            if (!positiveInterval(occupancy.startAt(), occupiedEnd)) continue;
            for (Allocation allocation : byResource.getOrDefault(occupancy.resourceId(), List.of()))
            {
                Segment segment = segments.get(allocation.segmentId());
                Job job = segment == null ? null : jobs.get(segment.jobId());
                if (job != null && "CARRY".equals(job.jobType())
                        && Objects.equals(job.carryRunId(), occupancy.executionRunId())) continue;
                if (segment != null && overlaps(segment.startAt(), segment.endAt(), occupancy.startAt(), occupiedEnd))
                    problems.add(problem(ReasonCode.RESOURCE_OVERLAP, "PLAN_ALLOCATION", allocation.allocationId(),
                            "resourceId", "计划与现场实际占用重叠", "候选不得覆盖同一资源的真实执行占用区间",
                            range(max(segment.startAt(), occupancy.startAt()), min(segment.endAt(), occupiedEnd))));
            }
        }
    }

    private void validatePhaseDurations(SolverInput input, Map<String, Job> jobs,
            Map<String, List<Segment>> segmentsByJob, Map<String, Task> tasks,
            Map<String, SolverInput.SharedBatchCandidate> sharedByMembers, List<Problem> problems)
    {
        for (Job job : jobs.values())
        {
            Task task = job.memberTaskIds().stream().map(tasks::get).filter(Objects::nonNull).findFirst().orElse(null);
            if (task == null) continue;
            List<Phase> phases = task.phases().stream().sorted(Comparator.comparingInt(Phase::sequenceNo)).toList();
            Instant previousEnd = null;
            if ("CARRY".equals(job.jobType()))
            {
                SolverInput.ActualOccupancy occupancy = input.actualOccupancies().stream()
                        .filter(value -> Objects.equals(value.executionRunId(), job.carryRunId()))
                        .filter(value -> value.releaseConfidence() == SolverInput.ReleaseConfidence.TRUSTED
                                && value.endAt() == null && !value.members().isEmpty())
                        .findFirst().orElse(null);
                List<Segment> carrySegments = segmentsByJob.getOrDefault(job.jobId(), List.of());
                List<Segment> current = occupancy == null || occupancy.currentPhase() == null ? List.of()
                        : carrySegments.stream().filter(segment ->
                                segment.phaseId().equals(occupancy.currentPhase().phaseId())
                                        && segment.phaseType() == occupancy.currentPhase().phaseType()).toList();
                int currentSequence = occupancy == null || occupancy.currentPhase() == null ? Integer.MAX_VALUE
                        : phases.stream().filter(phase -> phase.phaseId().equals(occupancy.currentPhase().phaseId()))
                                .mapToInt(Phase::sequenceNo).findFirst().orElse(Integer.MAX_VALUE);
                phases = phases.stream().filter(phase -> phase.sequenceNo() > currentSequence).toList();
                Set<String> allowedPhaseIds = new HashSet<>();
                if (occupancy != null && occupancy.currentPhase() != null)
                    allowedPhaseIds.add(occupancy.currentPhase().phaseId());
                phases.stream().map(Phase::phaseId).forEach(allowedPhaseIds::add);
                Set<String> laterPhaseIds = phases.stream().map(Phase::phaseId).collect(Collectors.toSet());
                boolean extraPhase = carrySegments.stream().anyMatch(segment -> !allowedPhaseIds.contains(segment.phaseId()));
                Instant expectedEnd = phases.isEmpty() ? occupancy == null ? null : occupancy.releaseAt()
                        : carrySegments.stream().filter(segment -> laterPhaseIds.contains(segment.phaseId()))
                                .map(Segment::endAt).max(Comparator.naturalOrder()).orElse(null);
                boolean validCarry = occupancy != null && current.size() == 1
                        && current.get(0).startAt().equals(input.horizon().planningAnchorAt())
                        && current.get(0).endAt().equals(occupancy.releaseAt()) && !extraPhase
                        && job.startAt().equals(input.horizon().planningAnchorAt())
                        && Objects.equals(job.endAt(), expectedEnd);
                if (!validCarry)
                    problems.add(problem(ReasonCode.INVALID_INTERVAL, "PLAN_JOB", job.jobId(), "carryRunId",
                            "carry 固定阶段无效",
                            "carry 必须从 planningAnchorAt 到可信 releaseAt 保留唯一当前阶段固定段，并只按工艺顺序追加全部后续阶段", null));
                previousEnd = occupancy == null ? null : occupancy.releaseAt();
            }
            for (Phase phase : phases)
            {
                List<Segment> values = segmentsByJob.getOrDefault(job.jobId(), List.of()).stream()
                        .filter(segment -> segment.phaseId().equals(phase.phaseId()))
                        .sorted(Comparator.comparing(Segment::startAt)).toList();
                long scheduled = values.stream().mapToLong(value -> Duration.between(value.startAt(), value.endAt()).getSeconds()).sum();
                int runs = physicalRuns(values);
                SolverInput.SharedBatchCandidate shared = "SHARED_BATCH".equals(job.jobType())
                        ? sharedByMembers.get(memberKey(job.memberTaskIds())) : null;
                long required = shared != null && phase.phaseType() == SolverInput.PhaseType.RUN
                        ? shared.cycleDurationSeconds()
                        : phase.fixedSeconds() + decimal(phase.secondsPerUnit())
                                .multiply(decimal(job.plannedQuantity()))
                                .setScale(0, RoundingMode.CEILING).longValueExact();
                required += Math.max(0, runs - 1) * (long) phase.resumeSetupSeconds();
                boolean shortRun = false;
                if (phase.interruptible())
                {
                    List<Long> runDurations = physicalRunDurations(values);
                    for (int index = 0; index < runDurations.size(); index++)
                    {
                        long productive = runDurations.get(index) - (index == 0 ? 0 : phase.resumeSetupSeconds());
                        if (productive < phase.minSegmentSeconds()) shortRun = true;
                    }
                }
                if (scheduled < required || (!phase.interruptible() && runs != 1)
                        || runs > phase.maxSegments() || shortRun)
                    problems.add(problem(ReasonCode.INVALID_INTERVAL, "OPERATION_PHASE", phase.phaseId(), null,
                            "阶段分段或时长无效", "计划时长必须覆盖标准工时；不可中断阶段不能跨空档；可中断阶段必须满足最小分段、恢复准备和最大分段数", null));
                if (!values.isEmpty() && previousEnd != null && values.get(0).startAt().isBefore(previousEnd))
                    problems.add(problem(ReasonCode.PRECEDENCE_VIOLATION, "OPERATION_PHASE", phase.phaseId(), null,
                            "阶段顺序冲突", "同一作业内的后续阶段不能早于前一阶段结束", range(values.get(0).startAt(), previousEnd)));
                if (!values.isEmpty()) previousEnd = values.get(values.size() - 1).endAt();
            }
        }
    }

    private void validateReleaseConservation(SolverInput input, Map<String, Job> jobs,
            Map<String, List<Job>> jobsByTask,
            Map<String, List<Segment>> segmentsByJob, List<Problem> problems)
    {
        Set<String> requiredJobs = input.dependencies().stream()
                .filter(value -> value.relationType() == SolverInput.RelationType.QUANTITY)
                .map(SolverInput.Dependency::predecessorTaskId)
                .flatMap(taskId -> jobsByTask.getOrDefault(taskId, List.of()).stream())
                .map(Job::jobId).collect(Collectors.toSet());
        input.materialDemands().stream().map(SolverInput.MaterialDemand::sourceTaskId).filter(Objects::nonNull)
                .flatMap(taskId -> jobsByTask.getOrDefault(taskId, List.of()).stream())
                .map(Job::jobId).forEach(requiredJobs::add);
        jobs.values().stream().filter(job -> "SHARED_BATCH".equals(job.jobType()))
                .map(Job::jobId).forEach(requiredJobs::add);
        for (Job job : jobs.values().stream().filter(value -> requiredJobs.contains(value.jobId())).toList())
        {
            BigDecimal released = BigDecimal.ZERO;
            for (Segment segment : segmentsByJob.getOrDefault(job.jobId(), List.of()))
            {
                if ((segment.releaseAt() == null) != (segment.releaseQuantity() == null)
                        || (segment.releaseAt() != null && (segment.releaseAt().isBefore(segment.startAt())
                                || segment.releaseAt().isAfter(segment.endAt()))))
                    problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "PLAN_SEGMENT", segment.segmentId(),
                            "releaseAt", "分段释放事实无效", "释放时间与数量必须同时存在且释放时间位于所属分段内", null));
                if (segment.releaseQuantity() != null) released = released.add(decimal(segment.releaseQuantity()));
            }
            if (released.compareTo(decimal(job.plannedQuantity())) != 0)
                problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "PLAN_JOB", job.jobId(),
                        "plannedQuantity", "作业释放数量不守恒", "所有分段释放量合计必须精确等于作业计划量，尾批不能丢失", null));
        }
    }

    private void validateDependencies(SolverInput input, Map<String, List<Job>> jobsByTask,
            Map<String, List<Segment>> segmentsByJob, List<Problem> problems)
    {
        Map<String, SolverInput.Task> tasks = input.tasks().stream()
                .collect(Collectors.toMap(SolverInput.Task::taskId, Function.identity()));
        Map<String, BigDecimal> requiredRelease;
        try
        {
            requiredRelease = QuantityReleaseMath.requiredReleaseByDependency(input.dependencies(), tasks);
        }
        catch (IllegalArgumentException exception)
        {
            problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "PLAN_VERSION", input.planVersionId(),
                    "dependencies", "有限产出不足", exception.getMessage(), null));
            return;
        }
        for (Dependency dependency : input.dependencies())
        {
            List<Job> predecessors = jobsByTask.getOrDefault(dependency.predecessorTaskId(), List.of());
            List<Job> successors = jobsByTask.getOrDefault(dependency.successorTaskId(), List.of()).stream()
                    .filter(job -> !"CARRY".equals(job.jobType())).toList();
            if (predecessors.isEmpty() || successors.isEmpty()) continue;
            Instant allowed = predecessors.stream().map(Job::endAt).max(Comparator.naturalOrder()).orElseThrow()
                    .plusSeconds(dependency.lagSeconds());
            if (dependency.relationType() == SolverInput.RelationType.QUANTITY)
            {
                BigDecimal threshold = requiredRelease.get(dependency.dependencyId());
                BigDecimal released = BigDecimal.ZERO;
                allowed = null;
                for (Segment segment : predecessors.stream()
                        .flatMap(job -> segmentsByJob.getOrDefault(job.jobId(), List.of()).stream())
                        .filter(value -> value.releaseAt() != null && value.releaseQuantity() != null)
                        .sorted(Comparator.comparing(Segment::releaseAt)).toList())
                {
                    released = released.add(decimal(segment.releaseQuantity()));
                    if (released.compareTo(threshold) >= 0) { allowed = segment.releaseAt().plusSeconds(dependency.lagSeconds()); break; }
                }
                if (allowed == null)
                {
                    problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "DEPENDENCY", dependency.dependencyId(), null,
                            "数量门槛未释放", "前置作业的分段释放数量未达到启动门槛", null));
                    continue;
                }
            }
            for (Job successor : successors)
                if (successor.startAt().isBefore(allowed))
                    problems.add(problem(ReasonCode.PRECEDENCE_VIOLATION, "DEPENDENCY", dependency.dependencyId(), null,
                            "任务依赖被违反", "后继作业开始早于前置完成或数量释放时间加 lag",
                            range(successor.startAt(), allowed)));
        }
        MaterialReleaseMath.ReleasePlan materialPlan;
        try { materialPlan = MaterialReleaseMath.plan(input); }
        catch (IllegalArgumentException exception) { return; }
        for (SolverInput.MaterialDemand demand : input.materialDemands())
        {
            if (demand.materialStatus() == SolverInput.MaterialStatus.CANCELLED) continue;
            List<Job> successors = jobsByTask.getOrDefault(demand.targetTaskId(), List.of()).stream()
                    .filter(job -> !"CARRY".equals(job.jobType())).toList();
            if (successors.isEmpty()) continue;
            Instant allowed = materialPlan.externalReadyAtByDemand().get(demand.demandId());
            if (demand.sourceTaskId() != null)
            {
                Instant actualReady = materialPlan.internalReadyAtByDemand().get(demand.demandId());
                if (actualReady != null)
                {
                    allowed = actualReady;
                    for (Job successor : successors)
                        if (successor.startAt().isBefore(allowed))
                            problems.add(problem(ReasonCode.PRECEDENCE_VIOLATION, "MATERIAL_DEMAND", demand.demandId(), null,
                                    "任务早于实际放行物料就绪", "后继作业不能早于 M28/M29 已质量放行供给的就绪时间",
                                    range(successor.startAt(), allowed)));
                    continue;
                }
                List<Job> predecessors = jobsByTask.getOrDefault(demand.sourceTaskId(), List.of());
                if (predecessors.isEmpty()) continue;
                BigDecimal required = materialPlan.internalRequiredByDemand().get(demand.demandId());
                BigDecimal released = BigDecimal.ZERO;
                allowed = null;
                for (Segment segment : predecessors.stream()
                        .flatMap(job -> segmentsByJob.getOrDefault(job.jobId(), List.of()).stream())
                        .filter(value -> value.releaseAt() != null && value.releaseQuantity() != null)
                        .sorted(Comparator.comparing(Segment::releaseAt)).toList())
                {
                    released = released.add(decimal(segment.releaseQuantity()));
                    if (released.compareTo(required) >= 0) { allowed = segment.releaseAt(); break; }
                }
            }
            if (allowed == null)
                problems.add(problem(ReasonCode.QUANTITY_NOT_RELEASED, "MATERIAL_DEMAND", demand.demandId(), null,
                        "物料尚未形成可消费释放", "候选释放量或质量放行供给未达到本需求预约量", null));
            else for (Job successor : successors)
                if (successor.startAt().isBefore(allowed))
                    problems.add(problem(ReasonCode.PRECEDENCE_VIOLATION, "MATERIAL_DEMAND", demand.demandId(), null,
                            "任务早于物料就绪", "后继作业不能消费未来释放或尚未质量放行的物料",
                            range(successor.startAt(), allowed)));
        }
    }

    private void validateLocks(SolverInput input, Map<String, Job> jobs, PlanCandidate candidate, List<Problem> problems)
    {
        Map<String, Segment> segments = candidate.segments().stream()
                .collect(Collectors.toMap(Segment::segmentId, value -> value));
        Map<String, Allocation> allocations = candidate.allocations().stream()
                .collect(Collectors.toMap(Allocation::allocationId, value -> value));
        input.locks().forEach(lock -> {
            Instant actualStart;
            Instant actualEnd;
            Set<String> actualResources;
            switch (lock.targetType())
            {
                case "JOB" -> {
                    Job job = jobs.get(lock.targetId());
                    if (job == null)
                    {
                        problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), "targetId",
                                "锁定作业不存在", "锁必须解析到当前候选中的确定作业，不能自动解锁或忽略", null));
                        return;
                    }
                    actualStart = job.startAt();
                    actualEnd = job.endAt();
                    Set<String> jobSegments = candidate.segments().stream()
                            .filter(value -> value.jobId().equals(job.jobId())).map(Segment::segmentId)
                            .collect(Collectors.toSet());
                    actualResources = candidate.allocations().stream()
                            .filter(value -> jobSegments.contains(value.segmentId()))
                            .map(Allocation::resourceId).collect(Collectors.toSet());
                }
                case "SEGMENT" -> {
                    Segment segment = segments.get(lock.targetId());
                    if (segment == null)
                    {
                        problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), "targetId",
                                "锁定分段不存在", "分段锁不能自动提升为作业锁或被忽略", null));
                        return;
                    }
                    actualStart = segment.startAt();
                    actualEnd = segment.endAt();
                    actualResources = candidate.allocations().stream()
                            .filter(value -> value.segmentId().equals(segment.segmentId()))
                            .map(Allocation::resourceId).collect(Collectors.toSet());
                }
                case "ALLOCATION" -> {
                    Allocation allocation = allocations.get(lock.targetId());
                    Segment segment = allocation == null ? null : segments.get(allocation.segmentId());
                    if (allocation == null || segment == null)
                    {
                        problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), "targetId",
                                "锁定分配不存在", "分配锁必须保留其确定分段、需求席位和资源身份", null));
                        return;
                    }
                    actualStart = segment.startAt();
                    actualEnd = segment.endAt();
                    actualResources = Set.of(allocation.resourceId());
                }
                default -> {
                    problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), "targetType",
                            "锁定目标类型无效", "只允许 JOB、SEGMENT 或 ALLOCATION", null));
                    return;
                }
            }
            boolean timeMismatch = ("TIME".equals(lock.lockType()) || "FULL".equals(lock.lockType()))
                    && (!Objects.equals(lock.lockedStartAt(), actualStart)
                            || !Objects.equals(lock.lockedEndAt(), actualEnd));
            boolean resourceMismatch = ("RESOURCE".equals(lock.lockType()) || "FULL".equals(lock.lockType()))
                    && !actualResources.containsAll(lock.lockedResourceIds());
            if (timeMismatch || resourceMismatch)
                problems.add(problem(ReasonCode.LOCK_CONFLICT, "PLAN_LOCK", lock.lockId(), null,
                        "候选违反计划锁", "锁定时间或锁定资源未在目标粒度保持", range(actualStart, actualEnd)));
        });
    }

    private boolean eligible(Resource resource, ResourceRequirement requirement, Instant at)
    {
        if (resource.resourceType() != requirement.resourceType()) return false;
        if (decimal(resource.capacity()).compareTo(decimal(requirement.capacityDemand())) < 0) return false;
        if (requirement.requiredSkillCode() == null) return true;
        return resource.skills().stream().anyMatch(skill -> "ACTIVE".equals(skill.status())
                && requirement.requiredSkillCode().equals(skill.skillCode())
                && skill.skillLevel() >= requirement.minimumSkillLevel()
                && (skill.validFrom() == null || !at.isBefore(skill.validFrom()))
                && (skill.validTo() == null || at.isBefore(skill.validTo())));
    }

    private boolean hasCommonWindow(List<Resource> eligible, Map<String, List<AvailabilityWindow>> windows,
            ResourceRequirement requirement, Instant start, Instant end, long durationSeconds)
    {
        List<Instant> boundaries = eligible.stream().flatMap(resource -> windows.getOrDefault(resource.resourceId(), List.of()).stream())
                .flatMap(window -> java.util.stream.Stream.of(max(start, window.startAt()), min(end, window.endAt())))
                .distinct().sorted().toList();
        for (int i = 0; i + 1 < boundaries.size(); i++)
        {
            Instant left = boundaries.get(i), right = boundaries.get(i + 1);
            if (Duration.between(left, right).getSeconds() < durationSeconds) continue;
            long available = eligible.stream().filter(resource -> windows.getOrDefault(resource.resourceId(), List.of()).stream()
                    .anyMatch(window -> !left.isBefore(window.startAt()) && !right.isAfter(window.endAt())
                            && decimal(window.capacity()).compareTo(decimal(requirement.capacityDemand())) >= 0)).count();
            if (available >= requirement.seatCount()) return true;
        }
        return false;
    }

    private boolean hasSegmentableCapacity(List<Resource> eligible, Map<String, List<AvailabilityWindow>> windows,
            ResourceRequirement requirement, Instant start, Instant end, long durationSeconds, int maxSegments)
    {
        List<Instant> boundaries = eligible.stream().flatMap(resource -> windows.getOrDefault(resource.resourceId(), List.of()).stream())
                .flatMap(window -> java.util.stream.Stream.of(max(start, window.startAt()), min(end, window.endAt())))
                .distinct().sorted().toList();
        long total = 0;
        int segments = 0;
        boolean previousAvailable = false;
        for (int i = 0; i + 1 < boundaries.size(); i++)
        {
            Instant left = boundaries.get(i), right = boundaries.get(i + 1);
            long available = eligible.stream().filter(resource -> windows.getOrDefault(resource.resourceId(), List.of()).stream()
                    .anyMatch(window -> !left.isBefore(window.startAt()) && !right.isAfter(window.endAt())
                            && decimal(window.capacity()).compareTo(decimal(requirement.capacityDemand())) >= 0)).count();
            boolean currentAvailable = available >= requirement.seatCount();
            if (currentAvailable)
            {
                total += Duration.between(left, right).getSeconds();
                if (!previousAvailable) segments++;
            }
            previousAvailable = currentAvailable;
        }
        return total >= durationSeconds && segments <= maxSegments;
    }

    private ResourceRequirement findRequirement(Map<String, Task> tasks, Job job, String phaseId, String requirementId)
    {
        if (job == null) return null;
        return job.memberTaskIds().stream().map(tasks::get).filter(Objects::nonNull)
                .flatMap(task -> task.phases().stream()).filter(phase -> Objects.equals(phase.phaseId(), phaseId))
                .flatMap(phase -> phase.requirements().stream())
                .filter(requirement -> requirement.requirementId().equals(requirementId)).findFirst().orElse(null);
    }

    private int physicalRuns(List<Segment> segments)
    {
        if (segments.isEmpty()) return 0;
        int runs = 1;
        for (int index = 1; index < segments.size(); index++)
            if (!segments.get(index - 1).endAt().equals(segments.get(index).startAt())) runs++;
        return runs;
    }

    private List<Long> physicalRunDurations(List<Segment> segments)
    {
        List<Long> result = new ArrayList<>();
        Segment previous = null;
        for (Segment segment : segments)
        {
            long duration = Duration.between(segment.startAt(), segment.endAt()).getSeconds();
            if (previous == null || !previous.endAt().equals(segment.startAt()))
                result.add(duration);
            else
                result.set(result.size() - 1, result.get(result.size() - 1) + duration);
            previous = segment;
        }
        return result;
    }

    private String memberKey(List<String> taskIds)
    {
        return taskIds.stream().sorted().collect(Collectors.joining("\u0000"));
    }

    private BigDecimal memberQuantity(Job job, String taskId,
            Map<String, SolverInput.SharedBatchCandidate> sharedByMembers,
            Map<String, List<SolverInput.ActualOccupancy>> occupanciesByRun)
    {
        if ("CARRY".equals(job.jobType()))
            return occupanciesByRun.getOrDefault(job.carryRunId(), List.of()).stream().findFirst()
                    .flatMap(occupancy -> occupancy.members().stream()
                            .filter(member -> member.taskId().equals(taskId)).findFirst())
                    .map(member -> decimal(member.remainingQuantity())).orElse(BigDecimal.ZERO);
        if ("SHARED_BATCH".equals(job.jobType()))
        {
            SolverInput.SharedBatchCandidate batch = sharedByMembers.get(memberKey(job.memberTaskIds()));
            if (batch != null)
                return batch.members().stream().filter(member -> member.taskId().equals(taskId)).findFirst()
                        .map(member -> decimal(member.quantity())).orElse(BigDecimal.ZERO);
        }
        return job.memberTaskIds().size() == 1 && job.memberTaskIds().contains(taskId)
                ? decimal(job.plannedQuantity()) : BigDecimal.ZERO;
    }

    private long durationSeconds(Task task, Phase phase)
    {
        return phase.fixedSeconds() + decimal(phase.secondsPerUnit()).multiply(decimal(task.quantity()))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }

    private <T> Map<String, T> unique(List<T> values, Function<T, String> id, String objectType, List<Problem> problems)
    {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values)
        {
            String key = id.apply(value);
            if (result.putIfAbsent(key, value) != null)
                problems.add(problem(ReasonCode.CONTRACT_VALIDATION_FAILED, objectType, key, "id",
                        "对象 ID 重复", "同一输入集合中的对象 ID 必须唯一", null));
        }
        return result;
    }

    private ValidationResult result(SolverInput input, String candidateHash, ValidationResult.Scope scope,
            Instant validatedAt, boolean publishPrecheck, List<Problem> problems)
    {
        boolean pass = problems.stream().noneMatch(value -> value.severity() == Severity.ERROR);
        return new ValidationResult("1.0", "VALIDATION_RESULT", input.requestId(), input.planVersionId(), validatedAt,
                VERSION, scope, input.definitionRevision(), input.executionRevision(), input.inputHash(), candidateHash,
                pass ? ValidationResult.Status.PASS : ValidationResult.Status.FAIL,
                publishPrecheck && pass, problems);
    }

    private Problem problem(ReasonCode reason, String objectType, String objectId, String field,
            String title, String detail, TimeRange range)
    {
        String safeId = validUuid(objectId) ? objectId : "00000000-0000-4000-8000-000000000000";
        String problemId = UUID.nameUUIDFromBytes((reason + "\u0000" + objectType + "\u0000" + safeId + "\u0000" + field)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new Problem("1.0", "APS_PROBLEM", problemId, reason, null, Severity.ERROR, title, detail, false,
                List.of(new ObjectRef(objectType, safeId, field)), range, Map.of());
    }

    private boolean validUuid(String value)
    {
        try { UUID.fromString(value); return true; }
        catch (RuntimeException ignored) { return false; }
    }
    private boolean positiveInterval(Instant start, Instant end) { return start != null && end != null && end.isAfter(start); }
    private boolean overlaps(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd)
    {
        return positiveInterval(leftStart, leftEnd) && positiveInterval(rightStart, rightEnd)
                && leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }
    private TimeRange range(Instant start, Instant end) { return positiveInterval(start, end) ? new TimeRange(start, end) : null; }
    private BigDecimal decimal(String value) { try { return value == null ? BigDecimal.ZERO : new BigDecimal(value); } catch (NumberFormatException ignored) { return BigDecimal.ZERO; } }
    private Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
}
