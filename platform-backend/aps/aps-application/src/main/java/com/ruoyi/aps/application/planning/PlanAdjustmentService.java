package com.ruoyi.aps.application.planning;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import com.ruoyi.aps.solver.contract.ValidationResult;
import com.ruoyi.aps.validator.IndependentConstraintValidator;

/**
 * IMP-08 版本化人工计划入口：原候选保持不可变，调整意图被编译成新的 M19 求解请求。
 */
public final class PlanAdjustmentService
{
    private final PlanRepository plans;
    private final PlanWorkbenchService workbench;
    private final ApsTransactionOperations transactions;
    private final ImpactClosureService impacts = new ImpactClosureService();
    private final SolverInputCodec codec = new SolverInputCodec();
    private final IndependentConstraintValidator validator = new IndependentConstraintValidator();

    public PlanAdjustmentService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions)
    {
        this.plans = Objects.requireNonNull(plans);
        this.workbench = Objects.requireNonNull(workbench);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public CreatedAdjustment create(ResourceAccessScope access, Adjustment command, String actor)
    {
        Objects.requireNonNull(command);
        String reason = requiredText(command.reason(), "调整原因不能为空");
        PlanRepository.PlanDetail source = workbench.detail(access, command.basePlanVersionId());
        requireMutableSource(source, command.expectedBaseRowVersion());
        if (source.input() == null || source.candidateHash() == null)
            throw new ApsBusinessException(ApsErrorCode.CONFLICT, "当前候选缺少可复算的冻结输入或候选摘要");
        requireFreshSource(source);
        Target sourceTarget = resolveTarget(source, command.targetType(), command.targetId());
        AdjustmentLock adjustment = adjustmentLock(source, sourceTarget, command, reason);
        String nextPlanId = stableId("adjustment-plan", command.requestId());
        SolverInputCodec.EncodedInput encoded = compile(source, nextPlanId, command, adjustment);
        ValidationResult readiness = validator.validateInput(encoded.value(), encoded.bytes(), command.capturedAt());
        if (readiness.validationStatus() != ValidationResult.Status.PASS)
        {
            String details = readiness.problems().stream().limit(3)
                    .map(problem -> problem.reasonCode() + ": " + problem.detail())
                    .collect(java.util.stream.Collectors.joining("；"));
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST,
                    "人工计划意图未通过独立输入校验：" + details);
        }
        ImpactClosureService.ImpactClosure closure = impacts.calculate(source.input(), PlanCandidateProjection.from(source),
                Set.copyOf(sourceTarget.job().members().stream().map(PlanRepository.PlanMember::taskId).toList()),
                adjustment.changedResourceIds());

        CreatedPlan created = transactions.required(() -> {
            PlanRepository.PlanRecord existing = plans.findByRequestId(command.requestId()).orElse(null);
            if (existing != null)
            {
                if (!existing.inputHash().equals(encoded.value().inputHash())
                        || !Objects.equals(existing.baseVersionId(), source.plan().id()))
                    throw new ApsBusinessException(ApsErrorCode.IDEMPOTENCY_CONFLICT,
                            "同一 Idempotency-Key 已绑定不同人工计划意图");
                return new CreatedPlan(existing, true);
            }
            PlanRepository.PlanDetail current = workbench.detail(access, source.plan().id());
            requireMutableSource(current, command.expectedBaseRowVersion());
            requireFreshSource(current);
            PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord(nextPlanId, source.plan().id(),
                    plans.nextVersionNo(), "人工调整-" + command.requestId().substring(0, 8), command.requestId(),
                    encoded.value().definitionRevision(), encoded.value().executionRevision(),
                    encoded.value().inputHash(), "DRAFT", command.capturedAt(), command.capturedAt(), 0);
            plans.insertDraft(plan, encoded.bytes(), actor);
            return new CreatedPlan(plan, false);
        });
        return new CreatedAdjustment(created.plan(), created.reused(), source.candidateHash(), closure);
    }

    private SolverInputCodec.EncodedInput compile(PlanRepository.PlanDetail source, String nextPlanId,
            Adjustment command, AdjustmentLock adjustment)
    {
        SolverInput input = source.input();
        Map<String, List<String>> resourcesByJob = resourcesByJob(source);
        List<SolverInput.BaselineJob> baselineJobs = source.jobs().stream().map(job ->
                new SolverInput.BaselineJob(job.id(), job.members().stream().map(PlanRepository.PlanMember::taskId)
                        .sorted().toList(), job.startAt(), job.endAt(), resourcesByJob.getOrDefault(job.id(), List.of())))
                .sorted(Comparator.comparing(SolverInput.BaselineJob::baselineJobId)).toList();
        Map<String, SolverInput.PlanLock> locksById = new LinkedHashMap<>();
        // 已完成候选的 M24 是锁的当前权威集合：它既包含求解时继承的锁，也包含候选阶段新增的锁。
        // 不再与原 input.locks 合并，否则 M24 使用版本级主键后会把同一语义锁重复带入下一版本。
        for (PlanRepository.PlanLock lock : source.locks())
            locksById.put(lock.id(), translateLock(source, nextPlanId, lock));
        SolverInput.PlanLock intentLock = new SolverInput.PlanLock(stableId("adjustment-lock", command.requestId()),
                adjustment.targetType(), adjustment.targetId(), adjustment.lockType(), adjustment.startAt(),
                adjustment.endAt(), adjustment.resourceIds(), adjustment.reason());
        locksById.put(intentLock.lockId(), intentLock);
        List<SolverInput.PlanLock> locks = locksById.values().stream()
                .sorted(Comparator.comparing(SolverInput.PlanLock::lockId)).toList();
        SolverInput derived = new SolverInput(input.schemaVersion(), input.contractType(), command.requestId(),
                nextPlanId, input.capturedAt(), input.definitionRevision(), input.executionRevision(),
                "0".repeat(64), input.hashAlgorithm(), input.canonicalization(), input.modelVersion(), input.scope(),
                input.horizon(), new SolverInput.BaseVersion(source.plan().id(), source.plan().inputHash(),
                        input.horizon().planningAnchorAt(), baselineJobs), input.parameters(), input.resources(),
                input.availabilityWindows(), input.tasks(),
                input.dependencies(), input.materialSupplies(), input.materialDemands(), input.sharedBatchCandidates(),
                locks, input.actualOccupancies());
        return codec.encodeWithHash(derived);
    }

    private SolverInput.PlanLock translateLock(PlanRepository.PlanDetail source, String nextPlanId,
            PlanRepository.PlanLock lock)
    {
        String oldTargetId = switch (lock.targetType())
        {
            case "JOB" -> lock.jobId();
            case "SEGMENT" -> lock.segmentId();
            case "ALLOCATION" -> lock.allocationId();
            default -> throw new ApsBusinessException(ApsErrorCode.CONFLICT, "候选包含未知锁定目标类型");
        };
        Target target = resolveTarget(source, lock.targetType(), oldTargetId);
        String targetId = translatedTargetId(source, nextPlanId, target,
                target.allocation() == null ? null : target.allocation().resourceId());
        return new SolverInput.PlanLock(stableId("inherited-lock", nextPlanId, lock.id()), lock.targetType(),
                targetId, lock.lockType(), lock.lockedStartAt(),
                lock.lockedEndAt(), lock.lockedResourceId() == null ? List.of() : List.of(lock.lockedResourceId()),
                lock.reason());
    }

    private AdjustmentLock adjustmentLock(PlanRepository.PlanDetail source, Target target, Adjustment command,
            String reason)
    {
        Instant startAt = Objects.requireNonNull(command.requestedStartAt(), "requestedStartAt");
        Instant endAt = Objects.requireNonNull(command.requestedEndAt(), "requestedEndAt");
        if (!endAt.isAfter(startAt))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整结束时间必须晚于开始时间");
        SolverInput.Horizon horizon = source.input().horizon();
        if (startAt.isBefore(horizon.startAt()) || endAt.isAfter(horizon.endAt()))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整时间超出当前求解范围");
        long startMillis = java.time.Duration.between(horizon.startAt(), startAt).toMillis();
        long endMillis = java.time.Duration.between(horizon.startAt(), endAt).toMillis();
        long unitMillis = Math.multiplyExact(horizon.timeUnitSeconds(), 1000L);
        if (startMillis % unitMillis != 0 || endMillis % unitMillis != 0)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整时间必须与求解时间单位对齐");
        boolean timeChanged = !startAt.equals(target.startAt()) || !endAt.equals(target.endAt());
        String requestedResource = blankToNull(command.requestedResourceId());
        boolean resourceChanged = requestedResource != null && (target.allocation() == null
                || !requestedResource.equals(target.allocation().resourceId()));
        if (requestedResource != null && target.allocation() == null)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "换人或换设备必须指向具体资源分配");
        if (requestedResource != null) assertCandidateResource(source.input(), target, requestedResource);
        if (!timeChanged && !resourceChanged)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整意图没有改变时间或资源");
        String lockType = timeChanged && resourceChanged ? "FULL" : timeChanged ? "TIME" : "RESOURCE";
        String identityResource = target.allocation() == null ? null
                : requestedResource == null ? target.allocation().resourceId() : requestedResource;
        String nextTarget = translatedTargetId(source, stableId("adjustment-plan", command.requestId()), target,
                identityResource);
        List<String> lockedResources = resourceChanged ? List.of(requestedResource) : List.of();
        Set<String> changedResources = new LinkedHashSet<>();
        if (resourceChanged)
        {
            changedResources.add(target.allocation().resourceId());
            changedResources.add(requestedResource);
        }
        return new AdjustmentLock(target.type(), nextTarget, lockType,
                timeChanged ? startAt : null, timeChanged ? endAt : null, lockedResources, reason,
                Set.copyOf(changedResources));
    }

    private void assertCandidateResource(SolverInput input, Target target, String requestedResource)
    {
        PlanRepository.PlanAllocation allocation = target.allocation();
        SolverInput.Task task = input.tasks().stream().filter(value -> target.job().members().stream()
                .anyMatch(member -> member.taskId().equals(value.taskId()))).findFirst().orElseThrow();
        SolverInput.Phase phase = task.phases().stream().filter(value -> value.phaseId().equals(target.segment().phaseId()))
                .findFirst().orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                        "资源分配对应阶段已不在冻结输入中"));
        SolverInput.ResourceRequirement requirement = phase.requirements().stream()
                .filter(value -> value.requirementId().equals(allocation.requirementId())).findFirst()
                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                        "资源分配对应需求已不在冻结输入中"));
        if (!requirement.candidateResourceIds().contains(requestedResource))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "请求资源不是该席位的合格候选资源");
    }

    private String translatedTargetId(PlanRepository.PlanDetail source, String nextPlanId, Target target,
            String allocationResource)
    {
        String sourceId = stableSourceId(source.input(), target.job());
        String jobId = stableId("job", nextPlanId, sourceId);
        if (target.segment() == null) return jobId;
        SolverInput.Task representative = representative(source.input(), target.job());
        List<SolverInput.Phase> phases = representative.phases().stream()
                .sorted(Comparator.comparingInt(SolverInput.Phase::sequenceNo)).toList();
        int phaseNo = -1;
        boolean unstable = false;
        for (int index = 0; index < phases.size(); index++)
        {
            SolverInput.Phase phase = phases.get(index);
            if (phase.phaseId().equals(target.segment().phaseId())) { phaseNo = index + 1; break; }
            if (phase.interruptible()) unstable = true;
        }
        if (phaseNo < 0 || unstable)
            throw new ApsBusinessException(ApsErrorCode.CONFLICT,
                    "调整目标位于无法稳定映射的动态可中断阶段之后");
        String segmentId = stableId("segment", jobId, target.segment().phaseId(), Integer.toString(phaseNo),
                Integer.toString(target.segment().segmentNo()));
        if (target.allocation() == null) return segmentId;
        return stableId("allocation", segmentId, target.allocation().requirementId(),
                Integer.toString(target.allocation().seatNo()), allocationResource);
    }

    private String stableSourceId(SolverInput input, PlanRepository.PlanJob job)
    {
        List<String> members = job.members().stream().map(PlanRepository.PlanMember::taskId).sorted().toList();
        if (members.size() == 1) return members.get(0);
        Set<String> memberSet = Set.copyOf(members);
        return input.sharedBatchCandidates().stream().filter(value -> value.members().stream()
                .map(SolverInput.SharedBatchMember::taskId).collect(java.util.stream.Collectors.toSet())
                .equals(memberSet)).map(SolverInput.SharedBatchCandidate::candidateId).findFirst()
                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                        "共享批候选无法映射到冻结输入"));
    }

    private SolverInput.Task representative(SolverInput input, PlanRepository.PlanJob job)
    {
        Set<String> members = job.members().stream().map(PlanRepository.PlanMember::taskId)
                .collect(java.util.stream.Collectors.toSet());
        return input.tasks().stream().filter(value -> members.contains(value.taskId())).findFirst()
                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                        "候选作业无法映射到冻结任务"));
    }

    private Target resolveTarget(PlanRepository.PlanDetail detail, String targetType, String targetId)
    {
        String type = targetType == null ? "" : targetType.trim();
        if ("JOB".equals(type))
        {
            PlanRepository.PlanJob job = detail.jobs().stream().filter(value -> value.id().equals(targetId))
                    .findFirst().orElseThrow(() -> missingTarget());
            return new Target(type, job, null, null, job.startAt(), job.endAt());
        }
        if ("SEGMENT".equals(type))
        {
            PlanRepository.PlanSegment segment = detail.segments().stream()
                    .filter(value -> value.id().equals(targetId)).findFirst().orElseThrow(() -> missingTarget());
            PlanRepository.PlanJob job = detail.jobs().stream().filter(value -> value.id().equals(segment.jobId()))
                    .findFirst().orElseThrow(() -> missingTarget());
            return new Target(type, job, segment, null, segment.startAt(), segment.endAt());
        }
        if ("ALLOCATION".equals(type))
        {
            PlanRepository.PlanAllocation allocation = detail.allocations().stream()
                    .filter(value -> value.id().equals(targetId)).findFirst().orElseThrow(() -> missingTarget());
            PlanRepository.PlanSegment segment = detail.segments().stream()
                    .filter(value -> value.id().equals(allocation.segmentId())).findFirst()
                    .orElseThrow(() -> missingTarget());
            PlanRepository.PlanJob job = detail.jobs().stream().filter(value -> value.id().equals(segment.jobId()))
                    .findFirst().orElseThrow(() -> missingTarget());
            return new Target(type, job, segment, allocation, segment.startAt(), segment.endAt());
        }
        throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整目标类型无效");
    }

    private Map<String, List<String>> resourcesByJob(PlanRepository.PlanDetail detail)
    {
        Map<String, String> jobBySegment = new LinkedHashMap<>();
        detail.segments().forEach(value -> jobBySegment.put(value.id(), value.jobId()));
        Map<String, Set<String>> values = new LinkedHashMap<>();
        detail.allocations().forEach(value -> values.computeIfAbsent(jobBySegment.get(value.segmentId()),
                ignored -> new LinkedHashSet<>()).add(value.resourceId()));
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, value.stream().sorted().toList()));
        return result;
    }

    private void requireMutableSource(PlanRepository.PlanDetail detail, long expectedRowVersion)
    {
        if (!"FEASIBLE".equals(detail.plan().status()))
            throw new ApsBusinessException(ApsErrorCode.CONFLICT, "只有可行候选可以派生人工计划版本");
        if (detail.plan().rowVersion() != expectedRowVersion)
            throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "基线候选已变化，请刷新后重试");
    }

    private void requireFreshSource(PlanRepository.PlanDetail detail)
    {
        Instant latest = plans.latestPlanningFactUpdatedAt().orElse(null);
        if (latest != null && detail.input() != null && latest.isAfter(detail.input().capturedAt()))
            throw new ApsBusinessException(ApsErrorCode.STALE_VERSION,
                    "排程输入创建后主数据或现场事实已变化，请重新排程后再调整");
    }

    private String requiredText(String value, String message)
    {
        if (value == null || value.isBlank()) throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
        String result = value.trim();
        if (result.length() > 500) throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "调整原因不能超过 500 字符");
        return result;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String stableId(String... parts)
    {
        return UUID.nameUUIDFromBytes(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private ApsBusinessException missingTarget()
    {
        return new ApsBusinessException(ApsErrorCode.NOT_FOUND, "调整目标不存在于当前候选");
    }

    private record Target(String type, PlanRepository.PlanJob job, PlanRepository.PlanSegment segment,
            PlanRepository.PlanAllocation allocation, Instant startAt, Instant endAt) { }
    private record AdjustmentLock(String targetType, String targetId, String lockType, Instant startAt,
            Instant endAt, List<String> resourceIds, String reason, Set<String> changedResourceIds) { }
    private record CreatedPlan(PlanRepository.PlanRecord plan, boolean reused) { }

    public record Adjustment(String requestId, String basePlanVersionId, long expectedBaseRowVersion,
            Instant capturedAt, String targetType, String targetId, Instant requestedStartAt,
            Instant requestedEndAt, String requestedResourceId, String reason) { }
    public record CreatedAdjustment(PlanRepository.PlanRecord plan, boolean reused, String baseCandidateHash,
            ImpactClosureService.ImpactClosure impact) { }
}
