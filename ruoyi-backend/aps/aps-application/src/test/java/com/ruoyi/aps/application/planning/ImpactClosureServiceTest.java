package com.ruoyi.aps.application.planning;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactClosureServiceTest
{
    private final ImpactClosureService service = new ImpactClosureService();

    @Test
    void closesAcrossDependencyBatchMaterialOrderAndSharedResource()
    {
        SolverInput input = input();
        PlanCandidate candidate = candidate();

        ImpactClosureService.ImpactClosure closure = service.calculate(input, candidate, Set.of("t1"), Set.of());

        assertThat(closure.affectedTaskIds()).containsExactly("t1", "t2", "t3", "t4", "t5");
        assertThat(closure.affectedResourceIds()).containsExactly("r1");
        assertThat(closure.reasons().get("TASK:t2")).contains("DEPENDENCY:d12");
        assertThat(closure.reasons().get("TASK:t3")).contains("SHARED_BATCH:b23");
        assertThat(closure.reasons().get("TASK:t4")).contains("MATERIAL:m34");
        assertThat(closure.reasons().get("TASK:t5")).contains("RESOURCE_ALLOCATION:RESOURCE:r1");
        assertThat(closure.globalRevalidationRequired()).isTrue();
    }

    @Test
    void rejectsUnknownSeedInsteadOfSilentlyShrinkingScope()
    {
        assertThatThrownBy(() -> service.calculate(input(), candidate(), Set.of("missing"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未知任务");
    }

    private SolverInput input()
    {
        List<SolverInput.Task> tasks = List.of(task("t1", "o1"), task("t2", "o2"), task("t3", "o3"),
                task("t4", "o4"), task("t5", "o5"));
        return new SolverInput("1.0", "SOLVER_INPUT", "request", "plan", Instant.EPOCH, 1, 1, "hash",
                "SHA-256", "JCS-RFC8785", "aps-cpsat-v1", new SolverInput.Scope("site", List.of("w")),
                new SolverInput.Horizon(Instant.EPOCH, Instant.EPOCH.plusSeconds(1000), Instant.EPOCH.plusSeconds(2000),
                        Instant.EPOCH, 1, "UTC"), null, new SolverInput.Parameters("FORWARD", 10, 1, 1, 0, 0),
                List.of(resource("r1")), List.of(), tasks,
                List.of(new SolverInput.Dependency("d12", "t1", "t2", SolverInput.RelationType.FINISH_TO_START,
                        0, SolverInput.LagBasis.ELAPSED, null, null, null, null, false)),
                List.of(), List.of(new SolverInput.MaterialDemand("m34", "t4", "item", "t3",
                        SolverInput.DemandType.TRANSFER, "1", "PCS", null,
                        SolverInput.MaterialStatus.AVAILABLE, Instant.EPOCH)),
                List.of(new SolverInput.SharedBatchCandidate("b23", "MANUAL", "op", "wc", "key", "2", "PCS",
                        10, List.of(new SolverInput.SharedBatchMember("t2", "1", "PCS"),
                                new SolverInput.SharedBatchMember("t3", "1", "PCS")))), List.of(), List.of());
    }

    private PlanCandidate candidate()
    {
        PlanCandidate.Job j1 = new PlanCandidate.Job("j1", "NORMAL", "op", "wc", "1", "PCS",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(10), List.of("t4"));
        PlanCandidate.Job j2 = new PlanCandidate.Job("j2", "NORMAL", "op", "wc", "1", "PCS",
                Instant.EPOCH.plusSeconds(10), Instant.EPOCH.plusSeconds(20), List.of("t5"));
        PlanCandidate.Segment s1 = new PlanCandidate.Segment("s1", "j1", "p", SolverInput.PhaseType.RUN, 1,
                j1.startAt(), j1.endAt(), "1", j1.endAt(), "1");
        PlanCandidate.Segment s2 = new PlanCandidate.Segment("s2", "j2", "p", SolverInput.PhaseType.RUN, 1,
                j2.startAt(), j2.endAt(), "1", j2.endAt(), "1");
        return new PlanCandidate(Instant.EPOCH, Instant.EPOCH.plusSeconds(1000), List.of(j1, j2), List.of(s1, s2),
                List.of(allocation("a1", "s1"), allocation("a2", "s2")), List.of(), List.of());
    }

    private SolverInput.Task task(String id, String orderLineId)
    {
        return new SolverInput.Task(id, orderLineId, "op", "wc", SolverInput.PlanningClass.MANDATORY_DETAIL,
                "1", "PCS", Instant.EPOCH, null, List.of());
    }

    private SolverInput.Resource resource(String id)
    {
        return new SolverInput.Resource(id, SolverInput.ResourceType.MACHINE, "wc", true, "1", "SEAT", List.of());
    }

    private PlanCandidate.Allocation allocation(String id, String segmentId)
    {
        return new PlanCandidate.Allocation(id, segmentId, "req", "r1", SolverInput.ResourceType.MACHINE, 1, "1");
    }
}
