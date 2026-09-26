package com.ruoyi.aps.solver.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityReleaseMathTest
{
    @Test
    void roundsPieceRatioUpAndThenAlignsToTransferBatch()
    {
        SolverInput.Task source = task("00000000-0000-4000-8000-000000000001", "101");
        SolverInput.Dependency dependency = dependency("00000000-0000-4000-8000-000000000011",
                source.taskId(), task("00000000-0000-4000-8000-000000000002", "20").taskId(),
                null, "0.5", "20", false);

        assertThat(QuantityReleaseMath.gateQuantity(dependency, source)).isEqualByComparingTo("60");
    }

    @Test
    void reservesConsumedOutputOnceAndKeepsTailRelease()
    {
        SolverInput.Task source = task("00000000-0000-4000-8000-000000000001", "100");
        SolverInput.Task first = task("00000000-0000-4000-8000-000000000002", "20");
        SolverInput.Task second = task("00000000-0000-4000-8000-000000000003", "40");
        List<SolverInput.Dependency> dependencies = List.of(
                dependency("00000000-0000-4000-8000-000000000011", source.taskId(), first.taskId(), "20", null, "20", true),
                dependency("00000000-0000-4000-8000-000000000012", source.taskId(), second.taskId(), "20", null, "20", true));
        Map<String, SolverInput.Task> tasks = Map.of(source.taskId(), source, first.taskId(), first, second.taskId(), second);

        Map<String, BigDecimal> required = QuantityReleaseMath.requiredReleaseByDependency(dependencies, tasks);

        assertThat(required.get(dependencies.get(0).dependencyId())).isEqualByComparingTo("20");
        assertThat(required.get(dependencies.get(1).dependencyId())).isEqualByComparingTo("60");
        assertThat(QuantityReleaseMath.releaseMilestones(source, dependencies, required))
                .extracting(BigDecimal::toPlainString).containsExactly("20", "40", "60", "80", "100");
    }

    @Test
    void rejectsDoubleConsumptionBeyondFiniteOutput()
    {
        SolverInput.Task source = task("00000000-0000-4000-8000-000000000001", "50");
        SolverInput.Task first = task("00000000-0000-4000-8000-000000000002", "30");
        SolverInput.Task second = task("00000000-0000-4000-8000-000000000003", "30");
        List<SolverInput.Dependency> dependencies = List.of(
                dependency("00000000-0000-4000-8000-000000000011", source.taskId(), first.taskId(), "10", null, null, true),
                dependency("00000000-0000-4000-8000-000000000012", source.taskId(), second.taskId(), "10", null, null, true));

        assertThatThrownBy(() -> QuantityReleaseMath.requiredReleaseByDependency(dependencies,
                Map.of(source.taskId(), source, first.taskId(), first, second.taskId(), second)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("超过");
    }

    private SolverInput.Task task(String id, String quantity)
    {
        return new SolverInput.Task(id, "00000000-0000-4000-8000-000000000021",
                "00000000-0000-4000-8000-000000000022", "00000000-0000-4000-8000-000000000023",
                SolverInput.PlanningClass.MANDATORY_DETAIL, quantity, "PCS", Instant.EPOCH, null, List.of());
    }

    private SolverInput.Dependency dependency(String id, String source, String target, String qty, String ratio,
            String batch, boolean consumes)
    {
        return new SolverInput.Dependency(id, source, target, SolverInput.RelationType.QUANTITY, 0,
                SolverInput.LagBasis.ELAPSED, qty, ratio, batch, "PCS", consumes);
    }
}
