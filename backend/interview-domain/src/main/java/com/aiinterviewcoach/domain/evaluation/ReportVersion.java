package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;

/** 不可变 ReportVersion；配置变化不会重算历史版本。 */
public record ReportVersion(
        ResourceId id,
        ResourceId evaluationVersionId,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin schemaPin,
        ReportComposition composition,
        Instant createdAt
) {

    public ReportVersion {
        DomainPreconditions.requireNonNull(id, "reportVersionId");
        DomainPreconditions.requireNonNull(evaluationVersionId, "evaluationVersionId");
        DomainPreconditions.requireNonNull(schemaPin, "reportSchemaPin");
        DomainPreconditions.requireNonNull(composition, "reportComposition");
        DomainPreconditions.require(evaluationVersionId.equals(composition.evaluationVersionId()),
                com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "report composition evaluation version does not match");
        DomainPreconditions.requireNonNull(createdAt, "reportVersionCreatedAt");
    }
}
