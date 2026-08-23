package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** rubric-judgement-v1 的强类型产物；历史类名保留，不再表达数值总分。 */
public record RubricScore(
        ResourceId judgementId,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin schemaPin,
        String rubricVersionId,
        List<RubricDimensionScore> dimensions,
        List<String> limitations
) {

    public RubricScore {
        DomainPreconditions.requireNonNull(judgementId, "judgementId");
        DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
        rubricVersionId = DomainPreconditions.requireText(rubricVersionId, "rubricVersionId");
        dimensions = List.copyOf(DomainPreconditions.requireNonEmpty(dimensions, "rubricDimensions"));
        DomainPreconditions.require(dimensions.size() <= 32, DomainErrorCode.INVALID_ARGUMENT,
                "rubric dimension count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(dimensions.stream()
                        .map(RubricDimensionScore::dimensionId).toList()).size() == dimensions.size(),
                DomainErrorCode.INVALID_ARGUMENT, "rubric dimension IDs must be unique");
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        DomainPreconditions.require(limitations.size() <= 16, DomainErrorCode.INVALID_ARGUMENT,
                "rubric limitation count exceeds schema limit");
        limitations.forEach(value -> {
            DomainPreconditions.requireText(value, "rubricLimitation");
            DomainPreconditions.require(value.length() <= 500, DomainErrorCode.INVALID_ARGUMENT,
                    "rubric limitation is too long");
        });
    }

    public Set<ResourceId> referencedEvidenceIds() {
        return dimensions.stream().flatMap(value -> value.evidenceIds().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String toString() {
        return "RubricScore[judgementId=" + judgementId + ", schemaPin=" + schemaPin
                + ", rubricVersionId=" + rubricVersionId + ", dimensionCount=" + dimensions.size()
                + ", limitations=<redacted>]";
    }
}
