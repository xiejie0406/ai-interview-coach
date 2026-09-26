package com.ruoyi.aps.infrastructure.mysql.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsExecutionMapper;
import com.ruoyi.aps.solver.contract.SolverInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisExecutionRepositoryTest
{
    @Test
    void sharedRunProducesOnePhysicalOccupancyWithDistinctRemainingMembers()
    {
        ApsExecutionMapper mapper = mock(ApsExecutionMapper.class);
        String taskA = id(1), taskB = id(2);
        when(mapper.listPlanningOccupancies(List.of(taskA, taskB))).thenReturn(List.of(
                row(taskA, "60", "20", "100", "100", "40"),
                row(taskB, "40", "20", "100", "100", "40")));

        var result = new MybatisExecutionRepository(mapper).listPlanningOccupancies(List.of(taskA, taskB),
                Instant.parse("2026-09-14T00:10:00Z"), Instant.parse("2026-09-15T00:00:00Z"));

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.occupancyId()).isEqualTo(id(10));
            assertThat(value.taskId()).as("共享物理占用不能伪装成一个成员").isNull();
            assertThat(value.remainingQuantity()).isEqualTo("60");
            assertThat(value.remainingDurationSeconds()).isEqualTo(60);
            assertThat(value.releaseAt()).isEqualTo(Instant.parse("2026-09-14T00:11:00Z"));
            assertThat(value.releaseConfidence()).isEqualTo(SolverInput.ReleaseConfidence.TRUSTED);
            assertThat(value.sourceRequirementId()).isEqualTo(id(17));
            assertThat(value.sourceSeatNo()).isEqualTo(1);
            assertThat(value.capacityUsed()).isEqualTo("1");
            assertThat(value.members()).containsExactly(
                    new SolverInput.ActualOccupancyMember(taskA, "40", "PCS"),
                    new SolverInput.ActualOccupancyMember(taskB, "20", "PCS"));
        });
    }

    @Test
    void partialSharedRunFailsClosedWhenMemberAllocationIsNotKnown()
    {
        ApsExecutionMapper mapper = mock(ApsExecutionMapper.class);
        String taskA = id(1), taskB = id(2);
        Map<String, Object> first = row(taskA, "60", "0", "50", "100", "0");
        Map<String, Object> second = row(taskB, "40", "0", "50", "100", "0");
        when(mapper.listPlanningOccupancies(List.of(taskA, taskB))).thenReturn(List.of(first, second));

        var result = new MybatisExecutionRepository(mapper).listPlanningOccupancies(List.of(taskA, taskB),
                Instant.parse("2026-09-14T00:10:00Z"), Instant.parse("2026-09-15T00:00:00Z"));

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.releaseConfidence()).isEqualTo(SolverInput.ReleaseConfidence.UNKNOWN);
            assertThat(value.releaseAt()).isNull();
            assertThat(value.members()).isEmpty();
        });
    }

    private Map<String, Object> row(String taskId, String memberPlanned, String memberProcessed,
            String assigned, String jobPlanned, String runProcessed)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id(10));
        row.put("plan_job_id", id(11));
        row.put("execution_run_id", id(12));
        row.put("plan_segment_id", id(13));
        row.put("resource_id", id(14));
        row.put("activity_type", "RUN");
        row.put("start_at", Instant.parse("2026-09-14T00:00:00Z"));
        row.put("task_id", taskId);
        row.put("member_planned_qty", new BigDecimal(memberPlanned));
        row.put("uom_code", "PCS");
        row.put("job_planned_qty", new BigDecimal(jobPlanned));
        row.put("assigned_qty", new BigDecimal(assigned));
        row.put("run_status", "RUNNING");
        row.put("resource_type", "MACHINE");
        row.put("member_count", 2);
        row.put("operation_spec_id", id(15));
        row.put("operation_phase_id", id(16));
        row.put("phase_type", "RUN");
        row.put("resource_requirement_id", id(17));
        row.put("seat_no", 1);
        row.put("capacity_used", BigDecimal.ONE);
        row.put("segment_seconds", 100L);
        row.put("processed_qty", new BigDecimal(memberProcessed));
        row.put("run_processed_qty", new BigDecimal(runProcessed));
        return row;
    }

    private String id(int value)
    {
        return String.format("00000000-0000-4000-8000-%012d", value);
    }
}
