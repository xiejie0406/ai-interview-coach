package com.ruoyi.aps.application.planning;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;

/** 把 M20～M23 工作台读模型还原成独立校验器使用的候选契约。 */
final class PlanCandidateProjection
{
    private PlanCandidateProjection() { }

    static PlanCandidate from(PlanRepository.PlanDetail detail)
    {
        Map<String, SolverInput.ResourceType> types = detail.input().resources().stream().collect(
                java.util.stream.Collectors.toMap(SolverInput.Resource::resourceId, SolverInput.Resource::resourceType));
        return new PlanCandidate(detail.input().horizon().startAt(), detail.input().horizon().endAt(),
                detail.jobs().stream().map(value -> new PlanCandidate.Job(value.id(), value.jobType(),
                        value.operationSpecId(), value.workCenterId(), decimal(value.plannedQty()), value.uomCode(),
                        value.startAt(), value.endAt(), value.members().stream().map(PlanRepository.PlanMember::taskId)
                                .sorted().toList(), value.carryRunId()))
                        .sorted(Comparator.comparing(PlanCandidate.Job::jobId)).toList(),
                detail.segments().stream().map(value -> new PlanCandidate.Segment(value.id(), value.jobId(),
                        value.phaseId(), SolverInput.PhaseType.valueOf(value.phaseType()), value.segmentNo(),
                        value.startAt(), value.endAt(), decimal(value.plannedQty()), value.releaseAt(),
                        decimal(value.releaseQty()))).sorted(Comparator.comparing(PlanCandidate.Segment::jobId)
                                .thenComparingInt(PlanCandidate.Segment::segmentNo)).toList(),
                detail.allocations().stream().map(value -> new PlanCandidate.Allocation(value.id(), value.segmentId(),
                        value.requirementId(), value.resourceId(), types.get(value.resourceId()), value.seatNo(),
                        decimal(value.capacityUsed()))).sorted(Comparator.comparing(PlanCandidate.Allocation::allocationId))
                        .toList(),
                detail.input().tasks().stream()
                        .filter(value -> value.planningClass() == SolverInput.PlanningClass.FUTURE_CARRY_FORWARD)
                        .map(SolverInput.Task::taskId).sorted().toList(), List.of());
    }

    private static String decimal(BigDecimal value)
    {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
