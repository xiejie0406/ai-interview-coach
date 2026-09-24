package com.ruoyi.aps.application.planning;

import java.util.Objects;
import java.util.Set;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import com.ruoyi.aps.solver.contract.ValidationResult;

/** 用短事务创建幂等 M19 草稿；输入编译发生在事务外。 */
public final class PlanRequestService
{
    private final SolverInputCompiler compiler;
    private final PlanRepository plans;
    private final ApsTransactionOperations transactions;
    private final ResourceManagementService resources;

    public PlanRequestService(SolverInputCompiler compiler, PlanRepository plans, ApsTransactionOperations transactions,
            ResourceManagementService resources)
    {
        this.compiler = Objects.requireNonNull(compiler);
        this.plans = Objects.requireNonNull(plans);
        this.transactions = Objects.requireNonNull(transactions);
        this.resources = Objects.requireNonNull(resources);
    }

    public CreatedPlan create(ResourceAccessScope access, SolverInputCompiler.CompileRequest request, String actor)
    {
        SolverInputCompiler.Compilation compilation = compiler.compile(access, request);
        if (compilation.readiness().validationStatus() != ValidationResult.Status.PASS)
            throw new ApsValidationException(ApsErrorCode.INVALID_REQUEST, "求解输入尚未通过数据就绪检查",
                    compilation.readiness().problems().stream().map(problem -> {
                        var ref = problem.objectRefs().isEmpty() ? null : problem.objectRefs().get(0);
                        return new ApsValidationIssue(problem.reasonCode().name(),
                                ref == null ? "PLAN_VERSION" : ref.objectType(),
                                ref == null ? request.planVersionId() : ref.objectId(),
                                ref == null ? null : ref.field(), problem.detail());
                    }).toList());
        return transactions.required(() -> {
            var existing = plans.findByRequestId(request.requestId());
            if (existing.isPresent())
            {
                if (!existing.get().inputHash().equals(compilation.input().inputHash()))
                    throw new ApsBusinessException(ApsErrorCode.IDEMPOTENCY_CONFLICT,
                            "同一 requestId 已绑定不同求解输入");
                return new CreatedPlan(existing.get(), true);
            }
            PlanRepository.PlanRecord record = new PlanRepository.PlanRecord(request.planVersionId(),
                    compilation.input().baseVersion() == null ? null : compilation.input().baseVersion().planVersionId(),
                    plans.nextVersionNo(), "候选计划-" + request.requestId().substring(0, 8), request.requestId(),
                    compilation.input().definitionRevision(), compilation.input().executionRevision(),
                    compilation.input().inputHash(), "DRAFT", compilation.input().capturedAt(),
                    compilation.input().capturedAt(), 0);
            plans.insertDraft(record, compilation.json(), actor);
            return new CreatedPlan(record, false);
        });
    }

    public PlanRepository.PlanRequestSnapshot status(ResourceAccessScope access, String requestId)
    {
        PlanRepository.PlanRequestSnapshot snapshot = plans.findRequestSnapshot(requestId).orElseThrow(() ->
                new ApsBusinessException(ApsErrorCode.NOT_FOUND, "计划请求不存在"));
        assertVisible(access, snapshot);
        return snapshot;
    }

    public PlanRepository.PlanRequestSnapshot cancel(ResourceAccessScope access, String requestId, String actor)
    {
        return transactions.required(() -> {
            PlanRepository.PlanRequestSnapshot snapshot = status(access, requestId);
            if ("CANCELLED".equals(snapshot.plan().status())) return snapshot;
            if (!Set.of("DRAFT", "SOLVING").contains(snapshot.plan().status()))
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "只有待求解或求解中的计划请求可以取消");
            if (plans.requestCancellation(requestId, snapshot.plan().rowVersion(), actor) != 1)
                throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "计划请求状态已变化，请刷新后重试");
            return plans.findRequestSnapshot(requestId).orElseThrow();
        });
    }

    private void assertVisible(ResourceAccessScope access, PlanRepository.PlanRequestSnapshot snapshot)
    {
        if (access.allWorkshops()) return;
        Set<String> visible = resources.listWorkshops(access).stream()
                .map(value -> value.id()).collect(java.util.stream.Collectors.toSet());
        if (!visible.containsAll(snapshot.input().scope().workshopIds()))
            throw new ApsBusinessException(ApsErrorCode.NOT_FOUND, "计划请求不存在");
    }

    public record CreatedPlan(PlanRepository.PlanRecord plan, boolean reused) { }
}
