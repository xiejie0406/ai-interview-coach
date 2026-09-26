package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.solver.contract.SolverInput.PhaseType;
import com.ruoyi.aps.solver.contract.SolverInput.ResourceType;

/** 与 SolverResult.candidate 对齐的候选计划；本类型不表达“已经通过校验”。 */
public record PlanCandidate(Instant horizonStartAt, Instant horizonEndAt, List<Job> jobs,
        List<Segment> segments, List<Allocation> allocations, List<String> futureCarryForwardTaskIds,
        List<String> unplannedTaskIds)
{
    public PlanCandidate
    {
        jobs = copy(jobs); segments = copy(segments); allocations = copy(allocations);
        futureCarryForwardTaskIds = copy(futureCarryForwardTaskIds); unplannedTaskIds = copy(unplannedTaskIds);
    }
    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }

    public record Job(String jobId, String jobType, String operationSpecId, String workCenterId,
            String plannedQuantity, String uomCode, Instant startAt, Instant endAt, List<String> memberTaskIds,
            String carryRunId)
    {
        public Job { memberTaskIds = copy(memberTaskIds); }

        /** 兼容不携带现场 run 的普通、拆分和共享批候选。 */
        public Job(String jobId, String jobType, String operationSpecId, String workCenterId,
                String plannedQuantity, String uomCode, Instant startAt, Instant endAt,
                List<String> memberTaskIds)
        {
            this(jobId, jobType, operationSpecId, workCenterId, plannedQuantity, uomCode,
                    startAt, endAt, memberTaskIds, null);
        }
    }
    public record Segment(String segmentId, String jobId, String phaseId, PhaseType phaseType, int segmentNo,
            Instant startAt, Instant endAt, String plannedQuantity, Instant releaseAt, String releaseQuantity) { }
    public record Allocation(String allocationId, String segmentId, String requirementId, String resourceId,
            ResourceType resourceType, int seatNo, String capacityUsed) { }
}
