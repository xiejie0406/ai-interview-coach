package com.ruoyi.aps.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.Problem;
import com.ruoyi.aps.solver.contract.SolverResult;

/** 单 Worker 的一次“短事务领取—事务外求解—短事务保存”循环。 */
public final class ApsWorkerCycle
{
    private final PlanRepository plans;
    private final ApsTransactionOperations transactions;
    private final SolverFunction solver;
    private final Clock clock;
    private final Duration staleSolvingTimeout;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public ApsWorkerCycle(PlanRepository plans, ApsTransactionOperations transactions, SolverFunction solver,
            Clock clock)
    {
        this(plans, transactions, solver, clock, Duration.ofMinutes(5));
    }

    public ApsWorkerCycle(PlanRepository plans, ApsTransactionOperations transactions, SolverFunction solver,
            Clock clock, Duration staleSolvingTimeout)
    {
        this.plans = Objects.requireNonNull(plans);
        this.transactions = Objects.requireNonNull(transactions);
        this.solver = Objects.requireNonNull(solver);
        this.clock = Objects.requireNonNull(clock);
        this.staleSolvingTimeout = Objects.requireNonNull(staleSolvingTimeout);
        if (staleSolvingTimeout.isNegative() || staleSolvingTimeout.isZero())
            throw new IllegalArgumentException("遗留 SOLVING 回收窗口必须大于零");
    }

    public Optional<SolverResult> runOnce()
    {
        if (stopping.get()) return Optional.empty();
        Instant staleBefore = Instant.now(clock).minus(staleSolvingTimeout);
        Optional<PlanRepository.ClaimedPlan> claimed = transactions.required(() -> {
            plans.recoverStaleSolving(staleBefore, "aps-worker");
            return plans.claimNext("aps-worker");
        });
        if (claimed.isEmpty()) return Optional.empty();
        BooleanSupplier cancellation = () -> stopping.get()
                || plans.isCancellationRequested(claimed.get().plan().id());
        SolverResult solved;
        try { solved = solver.solve(claimed.get().input(), Instant.now(clock), cancellation); }
        catch (RuntimeException exception) { solved = workerFailure(claimed.get().input(), Instant.now(clock)); }
        final SolverResult result = solved;
        if (plans.isCancellationRequested(claimed.get().plan().id())) return Optional.of(result);
        transactions.required(() -> {
            if (plans.storeResult(claimed.get(), result, "aps-worker") != 1)
                throw new IllegalStateException("计划结果保存失败：状态或 rowVersion 已变化");
        });
        return Optional.of(result);
    }

    /** 停止新领取，并让正在运行的求解器通过既有取消探针尽快退出。 */
    public void requestStop() { stopping.set(true); }

    boolean isStopping() { return stopping.get(); }

    private SolverResult workerFailure(SolverInput input, Instant generatedAt)
    {
        Problem problem = new Problem("1.0", "APS_PROBLEM", UUID.nameUUIDFromBytes(
                ("worker-error\u0000" + input.planVersionId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                Problem.ReasonCode.INTERNAL_ERROR, null, Problem.Severity.ERROR, "求解 Worker 执行失败",
                "Worker 已将候选请求标记为失败；当前正式计划未改变", false,
                List.of(new Problem.ObjectRef("PLAN_VERSION", input.planVersionId(), null)), null, Map.of());
        SolverResult.SolverSummary summary = new SolverResult.SolverSummary(SolverResult.StopReason.WORKER_ERROR,
                0, null, null, null, null, null, 0, 0, input.parameters().solverSearchThreads(),
                input.parameters().randomSeed(), List.of());
        return new SolverResult("1.0", "SOLVER_RESULT", input.requestId(), input.planVersionId(), generatedAt,
                input.definitionRevision(), input.executionRevision(), input.inputHash(), input.modelVersion(),
                "ortools-9.15.6755", SolverResult.PlanStatus.FAILED, SolverResult.SolverStatus.UNKNOWN,
                null, summary, null, null, List.of(problem));
    }

    @FunctionalInterface
    public interface SolverFunction
    {
        SolverResult solve(SolverInput input, Instant generatedAt, BooleanSupplier cancellationRequested);
    }
}
