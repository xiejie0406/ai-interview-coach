package com.ruoyi.aps.application.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceAccessScope;

/**
 * IMP-08 候选锁管理。锁值只能从服务端已确认的 M20～M23 推导，客户端不能伪造时间或资源。
 */
public final class PlanLockService
{
    private final PlanRepository plans;
    private final PlanWorkbenchService workbench;
    private final ApsTransactionOperations transactions;

    public PlanLockService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions)
    {
        this.plans = Objects.requireNonNull(plans);
        this.workbench = Objects.requireNonNull(workbench);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public PlanRepository.PlanDetail create(ResourceAccessScope access, CreateLock command, String actor)
    {
        Objects.requireNonNull(command);
        String reason = requiredText(command.reason(), "锁定原因不能为空");
        TargetType targetType = enumValue(TargetType.class, command.targetType(), "锁定目标类型无效");
        LockType lockType = enumValue(LockType.class, command.lockType(), "锁定类型无效");
        return transactions.required(() -> {
            PlanRepository.PlanDetail detail = mutableDetail(access, command.planVersionId(),
                    command.expectedPlanRowVersion());
            DerivedTarget target = target(detail, targetType, command.targetId());
            String resourceId = deriveResource(detail, target, lockType, command.requestedResourceId());
            Instant startAt = lockType == LockType.RESOURCE ? null : target.startAt();
            Instant endAt = lockType == LockType.RESOURCE ? null : target.endAt();
            if (detail.locks().stream().anyMatch(lock -> sameLock(lock, target, lockType, resourceId)))
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "同一计划目标已经存在相同锁定");
            if (plans.advanceCandidateRevision(detail.plan().id(), detail.plan().rowVersion(), actor) != 1)
                throw stale();
            plans.insertLock(new PlanRepository.PlanLock(UUID.randomUUID().toString(), targetType.name(),
                    target.jobId(), target.segmentId(), target.allocationId(), lockType.name(), startAt, endAt,
                    resourceId, reason, 0), detail.plan().id(), actor);
            return workbench.detail(access, detail.plan().id());
        });
    }

    public PlanRepository.PlanDetail delete(ResourceAccessScope access, String planVersionId, String lockId,
            long expectedPlanRowVersion, long expectedLockRowVersion, String actor)
    {
        return transactions.required(() -> {
            PlanRepository.PlanDetail detail = mutableDetail(access, planVersionId, expectedPlanRowVersion);
            PlanRepository.PlanLock lock = detail.locks().stream().filter(value -> value.id().equals(lockId))
                    .findFirst().orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "计划锁不存在"));
            if (lock.rowVersion() != expectedLockRowVersion) throw stale();
            if (plans.advanceCandidateRevision(planVersionId, detail.plan().rowVersion(), actor) != 1)
                throw stale();
            if (plans.deleteLock(planVersionId, lockId, expectedLockRowVersion) != 1) throw stale();
            return workbench.detail(access, planVersionId);
        });
    }

    private PlanRepository.PlanDetail mutableDetail(ResourceAccessScope access, String planVersionId,
            long expectedRowVersion)
    {
        PlanRepository.PlanDetail detail = workbench.detail(access, planVersionId);
        if (!List.of("FEASIBLE", "CONFLICT").contains(detail.plan().status()))
            throw new ApsBusinessException(ApsErrorCode.CONFLICT, "只有可行或冲突候选允许修改锁，正式计划必须派生新版本");
        if (detail.plan().rowVersion() != expectedRowVersion) throw stale();
        return detail;
    }

    private DerivedTarget target(PlanRepository.PlanDetail detail, TargetType type, String targetId)
    {
        return switch (type)
        {
            case JOB -> detail.jobs().stream().filter(value -> value.id().equals(targetId)).findFirst()
                    .map(value -> new DerivedTarget(value.id(), null, null, value.startAt(), value.endAt()))
                    .orElseThrow(() -> targetMissing());
            case SEGMENT -> detail.segments().stream().filter(value -> value.id().equals(targetId)).findFirst()
                    .map(value -> new DerivedTarget(value.jobId(), value.id(), null,
                            value.startAt(), value.endAt())).orElseThrow(() -> targetMissing());
            case ALLOCATION -> detail.allocations().stream().filter(value -> value.id().equals(targetId)).findFirst()
                    .map(value -> {
                        PlanRepository.PlanSegment segment = detail.segments().stream()
                                .filter(candidate -> candidate.id().equals(value.segmentId())).findFirst()
                                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                                        "资源分配没有对应计划分段"));
                        return new DerivedTarget(segment.jobId(), segment.id(), value.id(),
                                segment.startAt(), segment.endAt());
                    }).orElseThrow(() -> targetMissing());
        };
    }

    private String deriveResource(PlanRepository.PlanDetail detail, DerivedTarget target, LockType lockType,
            String requestedResourceId)
    {
        if (lockType == LockType.TIME)
        {
            if (requestedResourceId != null && !requestedResourceId.isBlank())
                throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "时间锁不能指定资源");
            return null;
        }
        List<PlanRepository.PlanAllocation> candidates = detail.allocations().stream()
                .filter(value -> target.allocationId() != null ? value.id().equals(target.allocationId())
                        : target.segmentId() != null ? value.segmentId().equals(target.segmentId())
                        : segmentsOfJob(detail, target.jobId()).contains(value.segmentId()))
                .toList();
        String requested = requestedResourceId == null ? null : requestedResourceId.trim();
        if (target.allocationId() != null)
        {
            String actual = candidates.stream().findFirst().map(PlanRepository.PlanAllocation::resourceId)
                    .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                            "锁定目标没有对应资源分配"));
            if (requested != null && !requested.isEmpty() && !actual.equals(requested))
                throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "资源分配锁不能替换为其他资源");
            return actual;
        }
        if (requested == null || requested.isEmpty())
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "作业或分段的资源锁必须指定已分配资源");
        if (candidates.stream().noneMatch(value -> value.resourceId().equals(requested)))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "指定资源不属于当前锁定目标");
        return requested;
    }

    private java.util.Set<String> segmentsOfJob(PlanRepository.PlanDetail detail, String jobId)
    {
        return detail.segments().stream().filter(value -> value.jobId().equals(jobId))
                .map(PlanRepository.PlanSegment::id).collect(java.util.stream.Collectors.toSet());
    }

    private boolean sameLock(PlanRepository.PlanLock lock, DerivedTarget target, LockType type, String resourceId)
    {
        return lock.targetType().equals(target.allocationId() != null ? "ALLOCATION"
                : target.segmentId() != null ? "SEGMENT" : "JOB")
                && lock.jobId().equals(target.jobId())
                && Objects.equals(lock.segmentId(), target.segmentId())
                && Objects.equals(lock.allocationId(), target.allocationId())
                && lock.lockType().equals(type.name())
                && Objects.equals(lock.lockedResourceId(), resourceId);
    }

    private String requiredText(String value, String message)
    {
        if (value == null || value.isBlank()) throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
        return value.trim();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String message)
    {
        try { return Enum.valueOf(type, value == null ? "" : value.trim()); }
        catch (IllegalArgumentException exception) { throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message); }
    }

    private ApsBusinessException targetMissing()
    {
        return new ApsBusinessException(ApsErrorCode.NOT_FOUND, "锁定目标不存在于当前计划版本");
    }

    private ApsBusinessException stale()
    {
        return new ApsBusinessException(ApsErrorCode.STALE_VERSION, "计划或锁已经变化，请刷新后重试");
    }

    private enum TargetType { JOB, SEGMENT, ALLOCATION }
    private enum LockType { TIME, RESOURCE, FULL }

    private record DerivedTarget(String jobId, String segmentId, String allocationId,
            Instant startAt, Instant endAt) { }

    public record CreateLock(String planVersionId, long expectedPlanRowVersion, String targetType,
            String targetId, String lockType, String requestedResourceId, String reason) { }
}
