package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialReleaseMathTest
{
    @Test
    void excludesPendingQualityAndReservesReleasedExternalSupplyOnce()
    {
        SolverInput input = input(List.of(
                supply("s1", "30", at(1), "AVAILABLE"),
                supply("s2", "30", at(2), "PENDING_QUALITY"),
                supply("s3", "40", at(3), "AVAILABLE")),
                List.of(demand("d1", null, "20", SolverInput.MaterialStatus.AVAILABLE),
                        demand("d2", null, "40", SolverInput.MaterialStatus.AVAILABLE)));

        MaterialReleaseMath.ReleasePlan plan = MaterialReleaseMath.plan(input);

        assertThat(plan.externalReadyAtByDemand()).containsEntry("d1", at(1)).containsEntry("d2", at(3));
    }

    @Test
    void rejectsDemandThatWouldConsumeFutureOrDuplicateTaskOutput()
    {
        SolverInput input = input(List.of(), List.of(
                demand("d1", "t1", "60", SolverInput.MaterialStatus.UNAVAILABLE),
                demand("d2", "t1", "60", SolverInput.MaterialStatus.UNAVAILABLE)));

        assertThatThrownBy(() -> MaterialReleaseMath.plan(input)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有限产出");
    }

    private SolverInput input(List<SolverInput.MaterialSupply> supplies, List<SolverInput.MaterialDemand> demands)
    {
        return new SolverInput("1.0", "SOLVER_INPUT", "request", "plan", at(0), 1, 1, "hash", "SHA-256",
                "JCS-RFC8785", "aps-cpsat-v1", new SolverInput.Scope("site", List.of()),
                new SolverInput.Horizon(at(0), at(10), at(20), at(0), 60, "UTC"), null,
                new SolverInput.Parameters("FORWARD", 10, 1, 1, 0, 0), List.of(), List.of(),
                List.of(task("t1"), task("t2")), List.of(), supplies, demands, List.of(), List.of(), List.of());
    }

    private SolverInput.Task task(String id)
    {
        return new SolverInput.Task(id, "line", "op", "wc", SolverInput.PlanningClass.MANDATORY_DETAIL,
                "100", "PCS", at(0), null, List.of());
    }

    private SolverInput.MaterialSupply supply(String id, String quantity, Instant at, String quality)
    {
        return new SolverInput.MaterialSupply(id, "item", "FIXED_AVAILABLE", null, at, quantity, "PCS", quality);
    }

    private SolverInput.MaterialDemand demand(String id, String sourceTaskId, String quantity,
            SolverInput.MaterialStatus status)
    {
        return new SolverInput.MaterialDemand(id, "t2", "item", sourceTaskId,
                sourceTaskId == null ? SolverInput.DemandType.EXTERNAL : SolverInput.DemandType.TRANSFER,
                quantity, "PCS", "20", status, null);
    }

    private Instant at(int hour) { return Instant.EPOCH.plusSeconds(hour * 3600L); }
}
