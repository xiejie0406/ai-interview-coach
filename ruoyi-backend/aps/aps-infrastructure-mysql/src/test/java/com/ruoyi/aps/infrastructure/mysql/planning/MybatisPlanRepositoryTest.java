package com.ruoyi.aps.infrastructure.mysql.planning;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsPlanMapper;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import com.ruoyi.aps.solver.contract.SolverResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlanRepositoryTest
{
    @Test
    void delegatesStaleSolvingRecoveryWithCutoffAndActor()
    {
        ApsPlanMapper mapper = mock(ApsPlanMapper.class);
        Instant cutoff = Instant.parse("2026-09-15T00:00:00Z");
        when(mapper.recoverStaleSolving(cutoff, "aps-worker")).thenReturn(2);

        assertThat(new MybatisPlanRepository(mapper).recoverStaleSolving(cutoff, "aps-worker")).isEqualTo(2);
        verify(mapper).recoverStaleSolving(cutoff, "aps-worker");
    }

    @Test
    void restoresProblemsAndSolverOutcomeForWorkbenchDetail()
    {
        ApsPlanMapper mapper = mock(ApsPlanMapper.class);
        SolverInputCodec.EncodedInput encoded = new SolverInputCodec().encodeWithHash(input());
        Map<String, Object> row = new HashMap<>();
        row.put("id", "plan");
        row.put("base_version_id", null);
        row.put("version_no", 1L);
        row.put("version_name", "冲突候选");
        row.put("request_id", "request");
        row.put("definition_revision", 1L);
        row.put("execution_revision", 1L);
        row.put("input_hash", encoded.value().inputHash());
        row.put("status", "CONFLICT");
        row.put("created_at", Instant.EPOCH);
        row.put("updated_at", Instant.EPOCH);
        row.put("row_version", 2L);
        row.put("input_snapshot_json", new String(encoded.bytes(), StandardCharsets.UTF_8));
        row.put("solver_summary_json", "{\"solverStatus\":\"INFEASIBLE\",\"resultKind\":\"INFEASIBLE_PROVEN\"}");
        row.put("validation_summary_json", """
                {"candidateHash":"","problems":[{
                  "schemaVersion":"1.0","contractType":"APS_PROBLEM",
                  "problemId":"50000000-0000-4000-8000-000000000008",
                  "reasonCode":"NO_COMMON_WINDOW","constraintCode":"APS-VAL-08",
                  "severity":"ERROR","title":"资源没有共同可用窗口",
                  "detail":"任务与候选资源的净日历没有交集","retryable":false,
                  "objectRefs":[{"objectType":"TASK","objectId":"50000000-0000-4000-8000-000000000009","field":null}],
                  "timeRange":null,"measurements":{}
                }]}
                """);
        when(mapper.findPlanVersionById("plan")).thenReturn(row);
        when(mapper.findPlanJobMembers("plan")).thenReturn(List.of());
        when(mapper.findPlanSegments("plan")).thenReturn(List.of());
        when(mapper.findPlanAllocations("plan")).thenReturn(List.of());
        when(mapper.findBaselineLocks("plan")).thenReturn(List.of());

        PlanRepository.PlanDetail detail = new MybatisPlanRepository(mapper).findDetail("plan").orElseThrow();

        assertThat(detail.candidateHash()).isNull();
        assertThat(detail.solverStatus()).isEqualTo(SolverResult.SolverStatus.INFEASIBLE);
        assertThat(detail.resultKind()).isEqualTo(SolverResult.ResultKind.INFEASIBLE_PROVEN);
        assertThat(detail.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.reasonCode()).isEqualTo(com.ruoyi.aps.solver.contract.Problem.ReasonCode.NO_COMMON_WINDOW);
            assertThat(problem.title()).isEqualTo("资源没有共同可用窗口");
            assertThat(problem.objectRefs()).singleElement().satisfies(ref ->
                    assertThat(ref.objectId()).isEqualTo("50000000-0000-4000-8000-000000000009"));
        });
    }

    @Test
    void persistsSharedBatchCapacityAndEachMembersOwnQuantity()
    {
        ApsPlanMapper mapper = mock(ApsPlanMapper.class);
        MybatisPlanRepository repository = new MybatisPlanRepository(mapper);
        SolverInput input = input();
        PlanCandidate.Job job = new PlanCandidate.Job("job", "SHARED_BATCH", "op", "wc", "100", "PCS",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(7200), List.of("a", "b"));
        PlanCandidate candidate = new PlanCandidate(Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), List.of(job),
                List.of(), List.of(), List.of(), List.of());
        SolverResult result = new SolverResult("1.0", "SOLVER_RESULT", "request", "plan", Instant.EPOCH, 1, 1,
                "hash", "aps-cpsat-v1", "test", SolverResult.PlanStatus.FEASIBLE,
                SolverResult.SolverStatus.OPTIMAL, SolverResult.ResultKind.FEASIBLE,
                new SolverResult.SolverSummary(SolverResult.StopReason.COMPLETED, 1, 1L, 0d, 0d, 0d, 0d,
                        1, 1, 1, 1, List.of()), "candidate", candidate, List.of());
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord("plan", null, 1, "plan", "request",
                1, 1, "hash", "SOLVING", Instant.EPOCH, Instant.EPOCH, 1);

        repository.storeResult(new PlanRepository.ClaimedPlan(plan, input), result, "worker");

        ArgumentCaptor<ApsPlanMapper.JobRow> jobs = ArgumentCaptor.forClass(ApsPlanMapper.JobRow.class);
        verify(mapper).insertJob(jobs.capture(), eq("worker"));
        assertThat(jobs.getValue().capacityValue()).isEqualByComparingTo("100");
        assertThat(jobs.getValue().compatibilityKey()).isEqualTo("RECIPE_A");
        ArgumentCaptor<ApsPlanMapper.MemberRow> members = ArgumentCaptor.forClass(ApsPlanMapper.MemberRow.class);
        verify(mapper, times(2)).insertMember(members.capture(), eq("worker"));
        assertThat(members.getAllValues()).extracting(ApsPlanMapper.MemberRow::taskId,
                value -> value.plannedQty().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("a", "60"),
                        org.assertj.core.groups.Tuple.tuple("b", "40"));
    }

    private SolverInput input()
    {
        SolverInput.SharedBatchCandidate batch = new SolverInput.SharedBatchCandidate("batch", "MANUAL_FIXED",
                "op", "wc", "RECIPE_A", "100", "PCS", 7200,
                List.of(new SolverInput.SharedBatchMember("a", "60", "PCS"),
                        new SolverInput.SharedBatchMember("b", "40", "PCS")));
        return new SolverInput("1.0", "SOLVER_INPUT", "request", "plan", Instant.EPOCH, 1, 1, "hash", "SHA-256",
                "JCS-RFC8785", "aps-cpsat-v1", new SolverInput.Scope("site", List.of()),
                new SolverInput.Horizon(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
                        Instant.EPOCH.plusSeconds(86400), Instant.EPOCH, 60, "UTC"), null,
                new SolverInput.Parameters("FORWARD", 10, 1, 1, 0, 0), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(batch), List.of(), List.of());
    }
}
