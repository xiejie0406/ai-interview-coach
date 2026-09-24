package com.ruoyi.aps.application.planning;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanLockServiceTest
{
    private static final Instant START = Instant.parse("2026-09-15T01:00:00Z");
    private final DirectTransactions transactions = new DirectTransactions();
    private final MemoryPlanRepository repository = new MemoryPlanRepository(detail("FEASIBLE", 3));
    private final PlanLockService service = service(repository);

    @Test
    void derivesAllocationResourceAndAdvancesCandidateRevision()
    {
        PlanRepository.PlanDetail result = service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", 3, "ALLOCATION", "allocation", "FULL", null, "班组已确认"), "planner");

        assertThat(result.plan().rowVersion()).isEqualTo(4);
        assertThat(result.locks()).singleElement().satisfies(lock -> {
            assertThat(lock.targetType()).isEqualTo("ALLOCATION");
            assertThat(lock.jobId()).isEqualTo("job");
            assertThat(lock.segmentId()).isEqualTo("segment");
            assertThat(lock.allocationId()).isEqualTo("allocation");
            assertThat(lock.lockType()).isEqualTo("FULL");
            assertThat(lock.lockedStartAt()).isEqualTo(START);
            assertThat(lock.lockedEndAt()).isEqualTo(START.plusSeconds(3600));
            assertThat(lock.lockedResourceId()).isEqualTo("machine");
        });
    }

    @Test
    void rejectsForgedTimeResourceAndStaleRevisionWithoutMutation()
    {
        assertThatThrownBy(() -> service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", 3, "SEGMENT", "segment", "TIME", "machine", "错误请求"), "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", 2, "SEGMENT", "segment", "TIME", null, "过期请求"), "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.STALE_VERSION));
        assertThat(repository.detail.plan().rowVersion()).isEqualTo(3);
        assertThat(repository.detail.locks()).isEmpty();
    }

    @Test
    void onlyCandidateCanChangeLocksAndDuplicateIsRejected()
    {
        MemoryPlanRepository published = new MemoryPlanRepository(detail("PUBLISHED", 3));
        assertThatThrownBy(() -> service(published).create(allAccess(), new PlanLockService.CreateLock(
                "plan", 3, "JOB", "job", "TIME", null, "正式计划不能原地改"), "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.CONFLICT));

        PlanRepository.PlanDetail created = service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", 3, "SEGMENT", "segment", "RESOURCE", "machine", "首条锁"), "planner");
        assertThatThrownBy(() -> service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", created.plan().rowVersion(), "SEGMENT", "segment", "RESOURCE", "machine", "重复锁"),
                "planner")).isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.CONFLICT));
        assertThat(repository.detail.plan().rowVersion()).isEqualTo(4);
    }

    @Test
    void deletesByBothPlanAndLockRevision()
    {
        PlanRepository.PlanDetail created = service.create(allAccess(), new PlanLockService.CreateLock(
                "plan", 3, "JOB", "job", "TIME", null, "待删除"), "planner");
        PlanRepository.PlanLock lock = created.locks().get(0);

        PlanRepository.PlanDetail result = service.delete(allAccess(), "plan", lock.id(), 4, 0, "planner");

        assertThat(result.plan().rowVersion()).isEqualTo(5);
        assertThat(result.locks()).isEmpty();
    }

    private PlanLockService service(MemoryPlanRepository plans)
    {
        ResourceRepository resources = (ResourceRepository) Proxy.newProxyInstance(
                ResourceRepository.class.getClassLoader(), new Class<?>[] { ResourceRepository.class },
                (proxy, method, arguments) -> List.of());
        PlanWorkbenchService workbench = new PlanWorkbenchService(plans,
                new ResourceManagementService(resources, transactions));
        return new PlanLockService(plans, workbench, transactions);
    }

    private ResourceAccessScope allAccess()
    {
        return new ResourceAccessScope("planner", true);
    }

    private static PlanRepository.PlanDetail detail(String status, long rowVersion)
    {
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord("plan", "base", 2, "候选计划", "request",
                1, 1, "a".repeat(64), status, START, START, rowVersion);
        PlanRepository.PlanMember member = new PlanRepository.PlanMember("member", "task", 1,
                BigDecimal.TEN, "PCS");
        PlanRepository.PlanJob job = new PlanRepository.PlanJob("job", "operation", "center", "JOB-1", "NORMAL",
                null, BigDecimal.TEN, "PCS", null, null, null, START, START.plusSeconds(3600), List.of(member));
        PlanRepository.PlanSegment segment = new PlanRepository.PlanSegment("segment", "job", "phase", 1, "RUN",
                START, START.plusSeconds(3600), BigDecimal.TEN, null, null, "PCS");
        PlanRepository.PlanAllocation allocation = new PlanRepository.PlanAllocation("allocation", "segment",
                "phase", "requirement", "machine", "MACHINE", 1, BigDecimal.ONE);
        return new PlanRepository.PlanDetail(plan, null, "b".repeat(64), List.of(job), List.of(segment),
                List.of(allocation), List.of(), List.of(), null, null);
    }

    private static final class DirectTransactions implements ApsTransactionOperations
    {
        @Override public <T> T required(Supplier<T> action) { return action.get(); }
    }

    private static final class MemoryPlanRepository implements PlanRepository
    {
        private PlanDetail detail;

        private MemoryPlanRepository(PlanDetail detail) { this.detail = detail; }

        @Override public Optional<PlanDetail> findDetail(String planVersionId)
        {
            return detail.plan().id().equals(planVersionId) ? Optional.of(detail) : Optional.empty();
        }

        @Override public int advanceCandidateRevision(String planVersionId, long expectedRowVersion, String actor)
        {
            if (!detail.plan().id().equals(planVersionId) || detail.plan().rowVersion() != expectedRowVersion
                    || !List.of("FEASIBLE", "CONFLICT").contains(detail.plan().status())) return 0;
            PlanRecord value = detail.plan();
            PlanRecord updated = new PlanRecord(value.id(), value.baseVersionId(), value.versionNo(),
                    value.versionName(), value.requestId(), value.definitionRevision(), value.executionRevision(),
                    value.inputHash(), value.status(), value.createdAt(), value.updatedAt(), value.rowVersion() + 1);
            detail = new PlanDetail(updated, detail.input(), detail.candidateHash(), detail.jobs(), detail.segments(),
                    detail.allocations(), detail.locks(), detail.problems(), detail.solverStatus(), detail.resultKind());
            return 1;
        }

        @Override public void insertLock(PlanLock lock, String planVersionId, String actor)
        {
            ArrayList<PlanLock> locks = new ArrayList<>(detail.locks());
            locks.add(lock);
            detail = new PlanDetail(detail.plan(), detail.input(), detail.candidateHash(), detail.jobs(),
                    detail.segments(), detail.allocations(), locks, detail.problems(), detail.solverStatus(), detail.resultKind());
        }

        @Override public int deleteLock(String planVersionId, String lockId, long expectedRowVersion)
        {
            ArrayList<PlanLock> locks = new ArrayList<>(detail.locks());
            boolean removed = locks.removeIf(value -> value.id().equals(lockId)
                    && value.rowVersion() == expectedRowVersion);
            if (removed) detail = new PlanDetail(detail.plan(), detail.input(), detail.candidateHash(), detail.jobs(),
                    detail.segments(), detail.allocations(), locks, detail.problems(), detail.solverStatus(), detail.resultKind());
            return removed ? 1 : 0;
        }

        @Override public Optional<PlanRecord> findByRequestId(String requestId) { return Optional.empty(); }
        @Override public Optional<PlanRequestSnapshot> findRequestSnapshot(String requestId) { return Optional.empty(); }
        @Override public Optional<BaselineSnapshot> findCurrentPublishedBaseline(String id) { return Optional.empty(); }
        @Override public long nextVersionNo() { return 0; }
        @Override public void insertDraft(PlanRecord plan, byte[] inputJson, String actor) { }
        @Override public Optional<ClaimedPlan> claimNext(String actor) { return Optional.empty(); }
        @Override public int requestCancellation(String requestId, long rowVersion, String actor) { return 0; }
        @Override public boolean isCancellationRequested(String planVersionId) { return false; }
        @Override public int storeResult(ClaimedPlan claim, SolverResult result, String actor) { return 0; }
    }
}
