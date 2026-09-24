package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.List;

/** SolverResult v1 顶层信封；求解器原生状态与业务结果状态保持分离。 */
public record SolverResult(String schemaVersion, String contractType, String requestId, String planVersionId,
        Instant generatedAt, long definitionRevision, long executionRevision, String inputHash,
        String modelVersion, String solverVersion, PlanStatus planStatus, SolverStatus solverStatus,
        ResultKind resultKind, SolverSummary solverSummary, String candidateHash, PlanCandidate candidate,
        List<Problem> problems)
{
    public SolverResult { problems = problems == null ? List.of() : List.copyOf(problems); }

    public enum PlanStatus { DRAFT, SOLVING, FEASIBLE, CONFLICT, CANCELLED, PUBLISHING, PUBLISHED, FAILED, SUPERSEDED }
    public enum SolverStatus { OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN, MODEL_INVALID }
    public enum ResultKind { FEASIBLE, TIMEOUT_WITH_SOLUTION, INFEASIBLE_PROVEN, TIMEOUT_NO_SOLUTION, INVALID_INPUT }
    public enum StopReason { COMPLETED, TIME_LIMIT, CANCELLED, INPUT_REJECTED, MODEL_INVALID, WORKER_ERROR, IN_PROGRESS, NOT_STARTED }
    public enum ObjectiveLevel { L0, L1, L2, L3, L4 }

    public record SolverSummary(StopReason stopReason, long wallTimeMillis, Long firstSolutionMillis,
            Double objectiveValue, Double bestBound, Double absoluteGap, Double relativeGap,
            int variableCount, int constraintCount, int searchThreads, int randomSeed,
            List<Objective> objectives)
    {
        public SolverSummary { objectives = objectives == null ? List.of() : List.copyOf(objectives); }
    }

    public record Objective(ObjectiveLevel level, String name, Double value, Double bestBound,
            boolean provenOptimal) { }
}
