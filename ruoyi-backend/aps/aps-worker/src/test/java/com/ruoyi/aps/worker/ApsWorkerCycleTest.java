package com.ruoyi.aps.worker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApsWorkerCycleTest
{
    @Test
    void claimsAndStoresInShortTransactionsButSolvesOutsideTransaction()
    {
        AtomicBoolean inTransaction = new AtomicBoolean();
        AtomicInteger transactions = new AtomicInteger();
        AtomicInteger stored = new AtomicInteger();
        AtomicInteger recoveries = new AtomicInteger();
        AtomicReference<Instant> staleBefore = new AtomicReference<>();
        ApsTransactionOperations transactionOperations = new ApsTransactionOperations() {
            @Override public <T> T required(Supplier<T> action)
            {
                assertTrue(inTransaction.compareAndSet(false, true));
                transactions.incrementAndGet();
                try { return action.get(); }
                finally { inTransaction.set(false); }
            }
        };
        SolverInput input = input();
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord(id(2), null, 1, "候选计划", id(1),
                7, 3, input.inputHash(), "SOLVING", at(0), at(0), 1);
        PlanRepository repository = new PlanRepository() {
            private boolean claimed;
            @Override public Optional<PlanRecord> findByRequestId(String requestId) { return Optional.empty(); }
            @Override public Optional<PlanRequestSnapshot> findRequestSnapshot(String requestId) { return Optional.empty(); }
            @Override public long nextVersionNo() { return 1; }
            @Override public void insertDraft(PlanRecord plan, byte[] inputJson, String actor) { }
            @Override public Optional<ClaimedPlan> claimNext(String actor)
            {
                assertTrue(inTransaction.get());
                if (claimed) return Optional.empty();
                claimed = true;
                return Optional.of(new ClaimedPlan(plan, input));
            }
            @Override public int recoverStaleSolving(Instant cutoff, String actor)
            {
                assertTrue(inTransaction.get());
                staleBefore.set(cutoff);
                recoveries.incrementAndGet();
                return 0;
            }
            @Override public Optional<BaselineSnapshot> findCurrentPublishedBaseline(String excludedPlanVersionId)
            {
                return Optional.empty();
            }
            @Override public int requestCancellation(String requestId, long rowVersion, String actor) { return 0; }
            @Override public boolean isCancellationRequested(String planVersionId) { return false; }
            @Override public int storeResult(ClaimedPlan claim, SolverResult result, String actor)
            {
                assertTrue(inTransaction.get());
                stored.incrementAndGet();
                return 1;
            }
        };
        ApsWorkerCycle cycle = new ApsWorkerCycle(repository, transactionOperations, (source, generatedAt, cancellation) -> {
            assertFalse(inTransaction.get());
            throw new IllegalStateException("simulated worker failure");
        }, Clock.fixed(at(4), ZoneOffset.UTC));

        assertEquals(SolverResult.PlanStatus.FAILED, cycle.runOnce().orElseThrow().planStatus());
        assertTrue(cycle.runOnce().isEmpty());
        assertEquals(3, transactions.get());
        assertEquals(1, stored.get());
        assertEquals(2, recoveries.get());
        assertEquals(at(4).minusSeconds(300), staleBefore.get());

        new ApsWorkerPoller(cycle).stop();
        assertTrue(cycle.isStopping());
        assertTrue(cycle.runOnce().isEmpty());
        assertEquals(3, transactions.get());
    }

    @Test
    void gracefulStopCancelsInFlightSolveAndPersistsCancelledResult()
    {
        SolverInput input = input();
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord(id(2), null, 1, "候选计划", id(1),
                7, 3, input.inputHash(), "SOLVING", at(0), at(0), 1);
        AtomicBoolean claimed = new AtomicBoolean();
        AtomicReference<SolverResult> stored = new AtomicReference<>();
        PlanRepository repository = new PlanRepository() {
            @Override public Optional<PlanRecord> findByRequestId(String requestId) { return Optional.empty(); }
            @Override public Optional<PlanRequestSnapshot> findRequestSnapshot(String requestId) { return Optional.empty(); }
            @Override public Optional<BaselineSnapshot> findCurrentPublishedBaseline(String excludedPlanVersionId)
            {
                return Optional.empty();
            }
            @Override public long nextVersionNo() { return 1; }
            @Override public void insertDraft(PlanRecord value, byte[] inputJson, String actor) { }
            @Override public Optional<ClaimedPlan> claimNext(String actor)
            {
                return claimed.compareAndSet(false, true)
                        ? Optional.of(new ClaimedPlan(plan, input)) : Optional.empty();
            }
            @Override public int requestCancellation(String requestId, long rowVersion, String actor) { return 0; }
            @Override public boolean isCancellationRequested(String planVersionId) { return false; }
            @Override public int storeResult(ClaimedPlan claim, SolverResult result, String actor)
            {
                stored.set(result);
                return 1;
            }
        };
        ApsTransactionOperations transactionOperations = new ApsTransactionOperations() {
            @Override public <T> T required(Supplier<T> action) { return action.get(); }
        };
        AtomicReference<ApsWorkerCycle> cycleReference = new AtomicReference<>();
        ApsWorkerCycle cycle = new ApsWorkerCycle(repository, transactionOperations,
                (source, generatedAt, cancellation) -> {
                    cycleReference.get().requestStop();
                    assertTrue(cancellation.getAsBoolean());
                    return cancelled(source, generatedAt);
                }, Clock.fixed(at(4), ZoneOffset.UTC));
        cycleReference.set(cycle);

        assertEquals(SolverResult.PlanStatus.CANCELLED, cycle.runOnce().orElseThrow().planStatus());
        assertEquals(SolverResult.PlanStatus.CANCELLED, stored.get().planStatus());
        assertTrue(cycle.isStopping());
        assertTrue(cycle.runOnce().isEmpty());
    }

    private SolverResult cancelled(SolverInput input, Instant generatedAt)
    {
        return new SolverResult("1.0", "SOLVER_RESULT", input.requestId(), input.planVersionId(), generatedAt,
                input.definitionRevision(), input.executionRevision(), input.inputHash(), "aps-cpsat-v1",
                "ortools-9.15.6755", SolverResult.PlanStatus.CANCELLED, SolverResult.SolverStatus.UNKNOWN,
                null, new SolverResult.SolverSummary(SolverResult.StopReason.CANCELLED, 0, null, null, null,
                        null, null, 0, 0, 1, 1, List.of()), null, null, List.of());
    }

    private SolverInput input()
    {
        return new SolverInput("1.0", "SOLVER_INPUT", id(1), id(2), at(0), 7, 3, "0".repeat(64),
                "SHA-256", "JCS-RFC8785", "aps-cpsat-v1", new SolverInput.Scope("SITE_01", List.of(id(3))),
                new SolverInput.Horizon(at(0), at(8), at(12), at(0), 60, "Asia/Shanghai"), null,
                new SolverInput.Parameters("FORWARD", 5, 1, 1, 0, 0), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Instant at(int hour) { return Instant.parse("2026-09-15T00:00:00Z").plusSeconds(hour * 3600L); }
    private String id(int no) { return String.format("00000000-0000-4000-8000-%012d", no); }
}
