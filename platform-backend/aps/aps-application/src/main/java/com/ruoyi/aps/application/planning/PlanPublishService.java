package com.ruoyi.aps.application.planning;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import com.ruoyi.aps.solver.contract.ValidationResult;
import com.ruoyi.aps.validator.IndependentConstraintValidator;

/** IMP-08 系统内发布：串行化短事务内复验候选并原子切换唯一正式版本。 */
public final class PlanPublishService
{
    private final PlanRepository plans;
    private final PlanWorkbenchService workbench;
    private final ApsTransactionOperations transactions;
    private final Clock clock;
    private final IndependentConstraintValidator validator = new IndependentConstraintValidator();
    private final SolverInputCodec codec = new SolverInputCodec();

    public PlanPublishService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions)
    {
        this(plans, workbench, transactions, Clock.systemUTC());
    }

    PlanPublishService(PlanRepository plans, PlanWorkbenchService workbench,
            ApsTransactionOperations transactions, Clock clock)
    {
        this.plans = Objects.requireNonNull(plans);
        this.workbench = Objects.requireNonNull(workbench);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public PublishedPlan publish(ResourceAccessScope access, String planVersionId, long expectedRowVersion,
            String reason, String actor)
    {
        String publishReason = requiredReason(reason);
        Instant publishedAt = clock.instant();
        return transactions.serializable(() -> {
            PlanRepository.PlanRecord current = plans.findCurrentPublishedForUpdate().orElse(null);
            PlanRepository.PlanRecord locked = plans.findPlanForUpdate(planVersionId)
                    .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "计划版本不存在"));
            PlanRepository.PlanDetail detail = workbench.detail(access, planVersionId);
            if ("PUBLISHED".equals(locked.status()) && current != null && current.id().equals(locked.id()))
            {
                assignDailyBaseline(locked.id(), detail, publishedAt, actor);
                return new PublishedPlan(workbench.detail(access, locked.id()), true, null, publishReason,
                        "NOT_APPLICABLE");
            }
            if (!"FEASIBLE".equals(locked.status()))
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "只有可行候选可以发布");
            if (locked.rowVersion() != expectedRowVersion || detail.plan().rowVersion() != expectedRowVersion)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "候选版本已变化，请刷新后重试");
            assertFreshFacts(detail);
            PlanCandidate candidate = PlanCandidateProjection.from(detail);
            if (detail.candidateHash() == null || !detail.candidateHash().equals(codec.candidateHash(candidate)))
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "候选摘要与 M20～M23 当前内容不一致");
            SolverInput effectiveInput = withCurrentLocks(detail);
            ValidationResult result = validator.validateCandidate(effectiveInput, candidate, detail.candidateHash(),
                    locked.definitionRevision(), locked.executionRevision(), ValidationResult.Scope.PUBLISH_PRECHECK,
                    publishedAt);
            if (!result.publishable()) throw validation(result);
            String expectedBaselineId = publicationBaseline(locked);
            String currentId = current == null ? null : current.id();
            if (!Objects.equals(expectedBaselineId, currentId))
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION,
                        "当前正式版本已变化，候选必须基于最新正式版本重新排程");
            if (current != null && plans.supersedeCurrent(current.id(), current.rowVersion(), publishedAt, actor) != 1)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "当前正式版本已被并发修改");
            if (plans.publishCandidate(locked.id(), locked.rowVersion(), publishedAt, publishReason, actor) != 1)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "候选已被并发修改");
            assignDailyBaseline(locked.id(), detail, publishedAt, actor);
            return new PublishedPlan(workbench.detail(access, locked.id()), false, currentId, publishReason,
                    "NOT_APPLICABLE");
        });
    }

    private void assignDailyBaseline(String planVersionId, PlanRepository.PlanDetail detail,
            Instant publishedAt, String actor)
    {
        String displayTimeZone = detail.input() == null || detail.input().horizon() == null
                ? "UTC" : detail.input().horizon().displayTimeZone();
        LocalDate businessDate = LocalDate.ofInstant(publishedAt, ZoneId.of(displayTimeZone));
        plans.assignDailyBaselineIfAbsent(planVersionId, businessDate, publishedAt, actor);
    }

    private void assertFreshFacts(PlanRepository.PlanDetail detail)
    {
        if (!Objects.equals(detail.plan().inputHash(), detail.input().inputHash()))
            throw new ApsBusinessException(ApsErrorCode.CONFLICT, "M19 输入摘要与冻结输入不一致");
        Instant latest = plans.latestPlanningFactUpdatedAt().orElse(null);
        if (latest != null && latest.isAfter(detail.input().capturedAt()))
            throw new ApsBusinessException(ApsErrorCode.STALE_VERSION,
                    "排程输入创建后主数据或现场事实已变化，请重新排程");
    }

    private String publicationBaseline(PlanRepository.PlanRecord candidate)
    {
        String cursor = candidate.baseVersionId();
        Set<String> visited = new HashSet<>();
        for (int depth = 0; cursor != null && depth < 32; depth++)
        {
            if (!visited.add(cursor)) throw new ApsBusinessException(ApsErrorCode.CONFLICT, "计划版本基线链形成循环");
            PlanRepository.PlanRecord ancestor = plans.findPlanForUpdate(cursor)
                    .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT, "计划版本基线不存在"));
            if (Set.of("PUBLISHED", "SUPERSEDED").contains(ancestor.status())) return ancestor.id();
            cursor = ancestor.baseVersionId();
        }
        if (cursor != null) throw new ApsBusinessException(ApsErrorCode.CONFLICT, "计划版本基线链过深");
        return null;
    }

    private SolverInput withCurrentLocks(PlanRepository.PlanDetail detail)
    {
        List<SolverInput.PlanLock> locks = detail.locks().stream().map(lock -> new SolverInput.PlanLock(lock.id(),
                lock.targetType(), switch (lock.targetType()) {
                    case "JOB" -> lock.jobId();
                    case "SEGMENT" -> lock.segmentId();
                    case "ALLOCATION" -> lock.allocationId();
                    default -> throw new ApsBusinessException(ApsErrorCode.CONFLICT, "候选包含未知锁定目标类型");
                }, lock.lockType(), lock.lockedStartAt(), lock.lockedEndAt(),
                lock.lockedResourceId() == null ? List.of() : List.of(lock.lockedResourceId()), lock.reason()))
                .toList();
        SolverInput source = detail.input();
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                source.availabilityWindows(), source.tasks(), source.dependencies(), source.materialSupplies(),
                source.materialDemands(), source.sharedBatchCandidates(), locks, source.actualOccupancies());
    }

    private ApsValidationException validation(ValidationResult result)
    {
        return new ApsValidationException(ApsErrorCode.CONFLICT, "候选未通过发布前独立复验",
                result.problems().stream().map(problem -> {
                    var ref = problem.objectRefs().isEmpty() ? null : problem.objectRefs().get(0);
                    return new ApsValidationIssue(problem.reasonCode().name(),
                            ref == null ? "PLAN_VERSION" : ref.objectType(),
                            ref == null ? "unknown" : ref.objectId(), ref == null ? null : ref.field(),
                            problem.detail());
                }).toList());
    }

    private String requiredReason(String reason)
    {
        if (reason == null || reason.isBlank())
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "发布原因不能为空");
        String value = reason.trim();
        if (value.length() > 500)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "发布原因不能超过 500 字符");
        return value;
    }

    public record PublishedPlan(PlanRepository.PlanDetail detail, boolean reused, String supersededVersionId,
            String reason, String outboundStatus) { }
}
