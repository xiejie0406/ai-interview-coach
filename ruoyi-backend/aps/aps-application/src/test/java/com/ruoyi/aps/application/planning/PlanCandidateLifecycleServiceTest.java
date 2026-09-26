package com.ruoyi.aps.application.planning;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import com.ruoyi.aps.application.resource.ResourceRepository;
import com.ruoyi.aps.solver.contract.SolverResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanCandidateLifecycleServiceTest
{
    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");

    @Test
    void discardsCandidateWithoutDeletingHistory()
    {
        MemoryPlanRepository repository = new MemoryPlanRepository(detail("FEASIBLE", 3));
        PlanCandidateLifecycleService.DiscardedCandidate result = service(repository).discard(
                new ResourceAccessScope("planner", true), "plan", 3, "  方案不再采用  ", "planner");

        assertThat(result.detail().plan().status()).isEqualTo("CANCELLED");
        assertThat(result.detail().plan().rowVersion()).isEqualTo(4);
        assertThat(result.reason()).isEqualTo("方案不再采用");
        assertThat(result.discardedAt()).isEqualTo(NOW);
        assertThat(result.detail().jobs()).hasSize(1);
        assertThat(repository.reason).isEqualTo("方案不再采用");
    }

    @Test
    void rejectsPublishedStaleAndBlankRequestsWithoutMutation()
    {
        MemoryPlanRepository published = new MemoryPlanRepository(detail("PUBLISHED", 3));
        assertThatThrownBy(() -> service(published).discard(new ResourceAccessScope("planner", true),
                "plan", 3, "不能废弃正式历史", "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.CONFLICT));

        MemoryPlanRepository feasible = new MemoryPlanRepository(detail("CONFLICT", 3));
        assertThatThrownBy(() -> service(feasible).discard(new ResourceAccessScope("planner", true),
                "plan", 2, "旧页面", "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.STALE_VERSION));
        assertThatThrownBy(() -> service(feasible).discard(new ResourceAccessScope("planner", true),
                "plan", 3, " ", "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_REQUEST));
        assertThat(feasible.detail.plan().status()).isEqualTo("CONFLICT");
    }

    private PlanCandidateLifecycleService service(MemoryPlanRepository repository)
    {
        DirectTransactions transactions = new DirectTransactions();
        ResourceRepository resources = (ResourceRepository) Proxy.newProxyInstance(
                ResourceRepository.class.getClassLoader(), new Class<?>[] { ResourceRepository.class },
                (proxy, method, arguments) -> List.of());
        PlanWorkbenchService workbench = new PlanWorkbenchService(repository,
                new ResourceManagementService(resources, transactions));
        return new PlanCandidateLifecycleService(repository, workbench, transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlanRepository.PlanDetail detail(String status, long rowVersion)
    {
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord("plan", null, 1, "候选", "request",
                1, 1, "a".repeat(64), status, NOW, NOW, rowVersion);
        PlanRepository.PlanJob job = new PlanRepository.PlanJob("job", "operation", "center", "JOB-1",
                "NORMAL", null, java.math.BigDecimal.ONE, "PCS", null, null, null,
                NOW, NOW.plusSeconds(60), List.of(new PlanRepository.PlanMember(
                        "member", "task", 1, java.math.BigDecimal.ONE, "PCS")));
        return new PlanRepository.PlanDetail(plan, null, "b".repeat(64), List.of(job), List.of(), List.of(),
                List.of(), List.of(), null, null);
    }

    private static final class DirectTransactions implements ApsTransactionOperations
    {
        @Override public <T> T required(Supplier<T> action) { return action.get(); }
    }

    private static final class MemoryPlanRepository implements PlanRepository
    {
        private PlanDetail detail;
        private String reason;

        private MemoryPlanRepository(PlanDetail detail) { this.detail = detail; }
        @Override public Optional<PlanDetail> findDetail(String id) { return Optional.of(detail); }
        @Override public Optional<PlanRecord> findPlanForUpdate(String id) { return Optional.of(detail.plan()); }
        @Override public int discardCandidate(String id, long expectedRowVersion, Instant discardedAt,
                String reason, String actor)
        {
            if (detail.plan().rowVersion() != expectedRowVersion
                    || !List.of("FEASIBLE", "CONFLICT").contains(detail.plan().status())) return 0;
            PlanRecord old = detail.plan();
            PlanRecord next = new PlanRecord(old.id(), old.baseVersionId(), old.versionNo(), old.versionName(),
                    old.requestId(), old.definitionRevision(), old.executionRevision(), old.inputHash(),
                    "CANCELLED", old.createdAt(), discardedAt, old.rowVersion() + 1);
            detail = new PlanDetail(next, detail.input(), detail.candidateHash(), detail.jobs(), detail.segments(),
                    detail.allocations(), detail.locks(), detail.problems(), detail.solverStatus(), detail.resultKind());
            this.reason = reason;
            return 1;
        }
        @Override public Optional<PlanRecord> findByRequestId(String id) { return Optional.empty(); }
        @Override public Optional<PlanRequestSnapshot> findRequestSnapshot(String id) { return Optional.empty(); }
        @Override public Optional<BaselineSnapshot> findCurrentPublishedBaseline(String id) { return Optional.empty(); }
        @Override public long nextVersionNo() { return 0; }
        @Override public void insertDraft(PlanRecord plan, byte[] inputJson, String actor) { }
        @Override public Optional<ClaimedPlan> claimNext(String actor) { return Optional.empty(); }
        @Override public int requestCancellation(String id, long rowVersion, String actor) { return 0; }
        @Override public boolean isCancellationRequested(String id) { return false; }
        @Override public int storeResult(ClaimedPlan claim, SolverResult result, String actor) { return 0; }
    }
}
