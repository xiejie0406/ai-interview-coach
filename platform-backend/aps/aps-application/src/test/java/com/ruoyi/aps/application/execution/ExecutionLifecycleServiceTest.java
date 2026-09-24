package com.ruoyi.aps.application.execution;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ActualOccupancy;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ExecutionRun;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OccupancyActivityType;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OccupancyStatus;
import com.ruoyi.aps.application.execution.ExecutionCatalog.PlannedResource;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReportTarget;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReportType;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.domain.execution.ExecutionAction;
import com.ruoyi.aps.domain.execution.ExecutionRunStatus;
import com.ruoyi.aps.domain.quantity.ProductionReportQuantities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionLifecycleServiceTest
{
    private static final Instant START = Instant.parse("2026-09-15T01:00:00Z");

    @Test
    void startsOnlyFirstSegmentAndAdvancesAtomicallyWithSafeReplay()
    {
        MemoryExecution memory = new MemoryExecution();
        ExecutionRepository repository = memory.proxy();
        ExecutionLifecycleService service = new ExecutionLifecycleService(repository, new DirectTransactions());
        ResourceAccessScope scope = new ResourceAccessScope("planner", true);

        ExecutionRun running = service.transition(scope, new ExecutionLifecycleService.Transition("run",
                ExecutionAction.START, 0, START, null), "planner");

        assertThat(running.status()).isEqualTo(ExecutionRunStatus.RUNNING);
        assertThat(memory.active).singleElement().satisfies(value -> {
            assertThat(value.planSegmentId()).isEqualTo("segment-1");
            assertThat(value.sourcePlanAllocationId()).isEqualTo("allocation-1");
        });

        ExecutionLifecycleService.PhaseAdvance command = new ExecutionLifecycleService.PhaseAdvance(
                "phase-request", "run", running.rowVersion(), START.plusSeconds(60), "进入加工阶段");
        var advanced = service.advancePhase(scope, command, "planner");

        assertThat(advanced.reused()).isFalse();
        assertThat(advanced.run().rowVersion()).isEqualTo(2);
        assertThat(memory.closed).singleElement()
                .satisfies(value -> assertThat(value.planSegmentId()).isEqualTo("segment-1"));
        assertThat(memory.active).singleElement().satisfies(value -> {
            assertThat(value.planSegmentId()).isEqualTo("segment-2");
            assertThat(value.sourcePlanAllocationId()).isEqualTo("allocation-2");
            assertThat(value.reason()).isEqualTo("进入加工阶段");
        });

        assertThat(service.advancePhase(scope, command, "planner").reused()).isTrue();
        assertThat(memory.closed).hasSize(1);
        assertThat(memory.active).hasSize(1);

        assertThatThrownBy(() -> service.advancePhase(scope,
                new ExecutionLifecycleService.PhaseAdvance("phase-request", "run", 1,
                        START.plusSeconds(60), "不同意图"), "planner"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsCompleteReportBeforeFinalSegmentWithoutWritingReport()
    {
        MemoryExecution memory = new MemoryExecution();
        memory.run = new ExecutionRun("run", "plan", "job", 1, ExecutionRunStatus.RUNNING,
                BigDecimal.TEN, "PCS", START, null, null, "create-request", 1);
        memory.active.add(new ActualOccupancy("occupancy", "job", "run", "segment-1", "allocation-1",
                "machine-1", OccupancyActivityType.SETUP, START, null, OccupancyStatus.ACTIVE,
                null, null, 0));
        AtomicBoolean inserted = new AtomicBoolean();
        QuantityRepository quantities = (QuantityRepository) Proxy.newProxyInstance(
                QuantityRepository.class.getClassLoader(), new Class<?>[] { QuantityRepository.class },
                (proxy, method, arguments) -> switch (method.getName())
                {
                    case "findReportByRequestId" -> Optional.empty();
                    case "findReportTargetForUpdate" -> Optional.of(new ReportTarget("job", "run", "member",
                            "task", "lot", "item", BigDecimal.TEN, BigDecimal.TEN, "PCS", false));
                    case "effectiveProcessedForRun", "effectiveProcessedForMember" -> BigDecimal.ZERO;
                    case "insertReport" -> { inserted.set(true); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        ProductionReportingService reporting = new ProductionReportingService(memory.proxy(), quantities,
                new DirectTransactions());
        var command = new ProductionReportingService.CreateReport("complete-request", "run", 1,
                "member", "task", ReportType.COMPLETE, START.plusSeconds(30),
                new ProductionReportQuantities(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), "PCS", null, null);

        assertThatThrownBy(() -> reporting.createReport(new ResourceAccessScope("planner", true), command,
                "planner")).isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_EXECUTION_TRANSITION));
        assertThat(inserted).isFalse();
    }

    private static final class DirectTransactions implements ApsTransactionOperations
    {
        @Override public <T> T required(Supplier<T> action) { return action.get(); }
    }

    private static final class MemoryExecution
    {
        private ExecutionRun run = new ExecutionRun("run", "plan", "job", 1, ExecutionRunStatus.READY,
                java.math.BigDecimal.TEN, "PCS", null, null, null, "create-request", 0);
        private final List<PlannedResource> planned = List.of(
                planned("segment-1", 1, "allocation-1", "machine-1", OccupancyActivityType.SETUP),
                planned("segment-2", 2, "allocation-2", "machine-1", OccupancyActivityType.RUN),
                planned("segment-3", 3, "allocation-3", "machine-1", OccupancyActivityType.UNLOAD));
        private final List<ActualOccupancy> active = new ArrayList<>();
        private final List<ActualOccupancy> closed = new ArrayList<>();

        private ExecutionRepository proxy()
        {
            return (ExecutionRepository) Proxy.newProxyInstance(ExecutionRepository.class.getClassLoader(),
                    new Class<?>[] { ExecutionRepository.class }, (proxy, method, arguments) -> switch (method.getName())
                    {
                        case "findRun", "findRunForUpdate" -> Optional.of(run);
                        case "isRunInScope", "isResourceReady" -> true;
                        case "hasResourceConflict", "hasProducedQuantity", "hasUnresolvedOutput",
                                "hasUnsupportedSameStart" -> false;
                        case "hasLaterPlannedSegment" -> hasLater();
                        case "listPlannedResources" -> planned;
                        case "listJobTaskIds" -> List.of();
                        case "listActiveOccupanciesForUpdate", "listOccupancies" -> List.copyOf(active);
                        case "listNextPhaseResources" -> next();
                        case "insertOccupancy" -> { active.add((ActualOccupancy) arguments[0]); yield null; }
                        case "closeOccupancy" -> close((String) arguments[0], (Instant) arguments[2]);
                        case "transitionRun" -> transition(arguments);
                        case "incrementRunRevision" -> increment((long) arguments[1]);
                        case "currentExecutionRevision" -> run.rowVersion();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private List<PlannedResource> next()
        {
            String segment = active.stream().findFirst().map(ActualOccupancy::planSegmentId).orElse(null);
            int current = planned.stream().filter(value -> value.planSegmentId().equals(segment))
                    .mapToInt(PlannedResource::segmentNo).findFirst().orElse(Integer.MAX_VALUE);
            int next = planned.stream().mapToInt(PlannedResource::segmentNo).filter(value -> value > current)
                    .min().orElse(Integer.MAX_VALUE);
            return planned.stream().filter(value -> value.segmentNo() == next).toList();
        }

        private boolean hasLater()
        {
            String segment = active.stream().filter(value -> value.planSegmentId() != null).findFirst()
                    .map(ActualOccupancy::planSegmentId).orElse(null);
            int current = planned.stream().filter(value -> value.planSegmentId().equals(segment))
                    .mapToInt(PlannedResource::segmentNo).findFirst().orElse(Integer.MIN_VALUE);
            return current == Integer.MIN_VALUE || planned.stream().anyMatch(value -> value.segmentNo() > current);
        }

        private int close(String id, Instant endAt)
        {
            ActualOccupancy value = active.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
            if (value == null) return 0;
            active.remove(value);
            closed.add(new ActualOccupancy(value.id(), value.planJobId(), value.executionRunId(),
                    value.planSegmentId(), value.sourcePlanAllocationId(), value.resourceId(), value.activityType(),
                    value.startAt(), endAt, OccupancyStatus.COMPLETED, value.correctionOfId(), value.reason(),
                    value.rowVersion() + 1));
            return 1;
        }

        private int transition(Object[] arguments)
        {
            long expected = (long) arguments[1];
            if (run.rowVersion() != expected) return 0;
            run = new ExecutionRun(run.id(), run.planVersionId(), run.planJobId(), run.runNo(),
                    (ExecutionRunStatus) arguments[3], run.assignedQty(), run.uomCode(), (Instant) arguments[4],
                    (Instant) arguments[5], (String) arguments[6], run.requestId(), run.rowVersion() + 1);
            return 1;
        }

        private int increment(long expected)
        {
            if (run.rowVersion() != expected) return 0;
            run = new ExecutionRun(run.id(), run.planVersionId(), run.planJobId(), run.runNo(), run.status(),
                    run.assignedQty(), run.uomCode(), run.actualStartAt(), run.actualEndAt(), run.pauseReason(),
                    run.requestId(), run.rowVersion() + 1);
            return 1;
        }

        private static PlannedResource planned(String segment, int no, String allocation, String resource,
                OccupancyActivityType type)
        {
            return new PlannedResource(segment, no, allocation, resource, type, false);
        }
    }
}
