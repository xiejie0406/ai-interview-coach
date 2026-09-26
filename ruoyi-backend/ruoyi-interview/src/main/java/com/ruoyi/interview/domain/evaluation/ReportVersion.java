package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;

/** 不可变 ReportVersion；配置变化不会重算历史版本。 */
public record ReportVersion(
        ResourceId id,
        ResourceId evaluationVersionId,
        com.ruoyi.interview.domain.platform.PromptSchemaPin schemaPin,
        ReportComposition composition,
        Instant createdAt
) {

    public ReportVersion {
        DomainPreconditions.requireNonNull(id, "reportVersionId");
        DomainPreconditions.requireNonNull(evaluationVersionId, "evaluationVersionId");
        DomainPreconditions.requireNonNull(schemaPin, "reportSchemaPin");
        DomainPreconditions.requireNonNull(composition, "reportComposition");
        DomainPreconditions.require(evaluationVersionId.equals(composition.evaluationVersionId()),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "report composition evaluation version does not match");
        DomainPreconditions.requireNonNull(createdAt, "reportVersionCreatedAt");
    }
}
