package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.List;

/** 独立校验结果。只有 PUBLISH_PRECHECK + PASS 才可能 publishable。 */
public record ValidationResult(String schemaVersion, String contractType, String requestId, String planVersionId,
        Instant validatedAt, String validatorVersion, Scope validationScope, long definitionRevision,
        long executionRevision, String inputHash, String candidateHash, Status validationStatus,
        boolean publishable, List<Problem> problems)
{
    public ValidationResult { problems = problems == null ? List.of() : List.copyOf(problems); }
    public enum Scope { INPUT, PLAN_CANDIDATE, PUBLISH_PRECHECK }
    public enum Status { PASS, FAIL }
}
