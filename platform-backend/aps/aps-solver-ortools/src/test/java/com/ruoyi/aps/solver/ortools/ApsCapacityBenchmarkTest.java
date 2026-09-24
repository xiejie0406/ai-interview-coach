package com.ruoyi.aps.solver.ortools;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * IMP-10 可重复容量基准。默认不进入普通回归，必须显式给出 aps.benchmark=true。
 * 输出只陈述本次实测，不在代码中预填 SLA。
 */
@EnabledIfSystemProperty(named = "aps.benchmark", matches = "true")
class ApsCapacityBenchmarkTest
{
    private static final Instant START = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void measuresFixedSyntheticShapes()
    {
        int[] counts = java.util.Arrays.stream(System.getProperty("aps.benchmark.counts", "1000,5000,20000")
                .split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        int resourceCount = Integer.getInteger("aps.benchmark.resources", 100);
        int maxSolveSeconds = Integer.getInteger("aps.benchmark.maxSolveSeconds", 30);
        int seed = Integer.getInteger("aps.benchmark.seed", 20260913);
        int threads = Integer.getInteger("aps.benchmark.threads", 1);
        System.out.printf("APS_BENCH_ENV java=%s vm=%s os=%s arch=%s processors=%d maxHeapBytes=%d jvmArgs=%s%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                ManagementFactory.getRuntimeMXBean().getInputArguments());
        for (int count : counts)
        {
            SolverInput input = input(count, resourceCount, maxSolveSeconds, seed, threads);
            long beforeHeap = usedHeap();
            long started = System.nanoTime();
            SolverResult result = new CpSatFiniteCapacitySolver().solve(input, Instant.now());
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            long heapDelta = Math.max(0, usedHeap() - beforeHeap);
            SolverResult.SolverSummary summary = result.solverSummary();
            System.out.printf("APS_BENCH_RESULT tasks=%d resources=%d horizonDays=30 seed=%d threads=%d "
                            + "maxSolveSeconds=%d elapsedMillis=%d heapDeltaBytes=%d planStatus=%s solverStatus=%s resultKind=%s "
                            + "stopReason=%s firstSolutionMillis=%s wallTimeMillis=%d objective=%s bestBound=%s "
                            + "variables=%d constraints=%d problems=%d%n",
                    count, resourceCount, seed, threads, maxSolveSeconds, elapsedMillis, heapDelta,
                    result.planStatus(), result.solverStatus(), result.resultKind(), summary.stopReason(), summary.firstSolutionMillis(),
                    summary.wallTimeMillis(), summary.objectiveValue(), summary.bestBound(), summary.variableCount(),
                    summary.constraintCount(), result.problems().size());
            assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
            assertEquals(SolverResult.ResultKind.FEASIBLE, result.resultKind());
            assertEquals(SolverResult.StopReason.COMPLETED, summary.stopReason());
            assertNotNull(summary.firstSolutionMillis(), "固定可行夹具必须在求解时限内产生首解");
            assertNotNull(result.candidate(), "固定可行夹具必须返回通过独立校验的候选");
        }
    }

    private SolverInput input(int taskCount, int resourceCount, int maxSolveSeconds, int seed, int threads)
    {
        if (taskCount < 1 || resourceCount < 1) throw new IllegalArgumentException("任务数和资源数必须为正数");
        Instant end = START.plus(30, ChronoUnit.DAYS);
        List<SolverInput.Resource> resources = new ArrayList<>(resourceCount);
        List<SolverInput.AvailabilityWindow> windows = new ArrayList<>(resourceCount);
        for (int i = 0; i < resourceCount; i++)
        {
            String resourceId = stable("resource", i);
            resources.add(new SolverInput.Resource(resourceId, SolverInput.ResourceType.MACHINE,
                    stable("center", i % 10), true, "1", "COUNT", List.of()));
            windows.add(new SolverInput.AvailabilityWindow(stable("window", i), resourceId, START, end, "1"));
        }
        List<SolverInput.Task> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++)
        {
            List<String> candidates = new ArrayList<>(Math.min(5, resourceCount));
            for (int j = 0; j < Math.min(5, resourceCount); j++)
                candidates.add(resources.get((i + j) % resourceCount).resourceId());
            SolverInput.ResourceRequirement requirement = new SolverInput.ResourceRequirement(
                    stable("requirement", i), SolverInput.ResourceType.MACHINE, 1, "1", null, null, candidates);
            SolverInput.Phase phase = new SolverInput.Phase(stable("phase", i), SolverInput.PhaseType.RUN,
                    1, 120, "0", false, 1, 0, List.of(requirement));
            tasks.add(new SolverInput.Task(stable("task", i), stable("line", i / 5), stable("operation", i % 20),
                    stable("center", i % 10), SolverInput.PlanningClass.MANDATORY_DETAIL, "1", "PCS",
                    START, end.minus(1, ChronoUnit.DAYS), List.of(phase)));
        }
        return new SolverInput("1.0", "SOLVER_INPUT", stable("request"), stable("plan"), START,
                1, 0, "0".repeat(64), "SHA-256", "JCS-RFC8785", "aps-cpsat-v1",
                new SolverInput.Scope("SITE_01", List.of(stable("workshop"))),
                new SolverInput.Horizon(START, end.minus(2, ChronoUnit.DAYS), end, START, 60, "Asia/Shanghai"),
                null, new SolverInput.Parameters("FORWARD", maxSolveSeconds, seed, threads, 0, 0),
                resources, windows, tasks, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private long usedHeap()
    {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private String stable(Object... parts)
    {
        return UUID.nameUUIDFromBytes(java.util.Arrays.toString(parts).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
