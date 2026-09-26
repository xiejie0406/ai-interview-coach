package com.ruoyi.aps.application.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCandidateProjectionTest
{
    private static final Instant START = Instant.parse("2026-09-15T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    @Test
    void restoresCarryRunIdentityWhenRebuildingPersistedCandidate()
    {
        String runId = "00000000-0000-4000-8000-000000000061";
        String resourceId = "00000000-0000-4000-8000-000000000001";
        SolverInput input = input(resourceId);
        PlanRepository.PlanMember member = new PlanRepository.PlanMember("member", "task", 1,
                BigDecimal.TEN, "PCS");
        PlanRepository.PlanJob job = new PlanRepository.PlanJob("job", "operation", "center", "CARRY-1",
                "CARRY", null, BigDecimal.TEN, "PCS", null, null, null, runId,
                START, END, List.of(member));
        PlanRepository.PlanSegment segment = new PlanRepository.PlanSegment("segment", job.id(), "phase", 1,
                "RUN", START, END, BigDecimal.TEN, END, BigDecimal.TEN, "PCS");
        PlanRepository.PlanAllocation allocation = new PlanRepository.PlanAllocation("allocation", segment.id(),
                "phase", "requirement", resourceId, "MACHINE", 1, BigDecimal.ONE);
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord("plan", null, 1, "carry", "request",
                1, 1, "0".repeat(64), "FEASIBLE", START, START, 1);
        PlanRepository.PlanDetail detail = new PlanRepository.PlanDetail(plan, input, null, List.of(job),
                List.of(segment), List.of(allocation), List.of(), List.of(), null, null);

        PlanCandidate projected = PlanCandidateProjection.from(detail);
        PlanCandidate expected = new PlanCandidate(input.horizon().startAt(), input.horizon().endAt(),
                List.of(new PlanCandidate.Job(job.id(), job.jobType(), job.operationSpecId(), job.workCenterId(),
                        "10", "PCS", START, END, List.of("task"), runId)),
                List.of(new PlanCandidate.Segment(segment.id(), job.id(), "phase", SolverInput.PhaseType.RUN, 1,
                        START, END, "10", END, "10")),
                List.of(new PlanCandidate.Allocation(allocation.id(), segment.id(), "requirement", resourceId,
                        SolverInput.ResourceType.MACHINE, 1, "1")), List.of(), List.of());

        assertThat(projected.jobs()).singleElement().satisfies(value ->
                assertThat(value.carryRunId()).isEqualTo(runId));
        assertThat(new SolverInputCodec().candidateHash(projected))
                .isEqualTo(new SolverInputCodec().candidateHash(expected));
    }

    private SolverInput input(String resourceId)
    {
        return new SolverInput("1.0", "SOLVER_INPUT", "request", "plan", START, 1, 1,
                "0".repeat(64), "SHA-256", "RFC8785-JCS", "aps-cpsat-v1",
                new SolverInput.Scope("SITE", List.of("workshop")),
                new SolverInput.Horizon(START, END, END, START, 60, "Asia/Shanghai"), null,
                new SolverInput.Parameters("FORWARD", 10, 1, 1, 0, 0),
                List.of(new SolverInput.Resource(resourceId, SolverInput.ResourceType.MACHINE, "center", true,
                        "1", "COUNT", List.of())), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }
}
