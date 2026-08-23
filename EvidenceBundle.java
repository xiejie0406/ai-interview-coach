package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** 经 evidence-extraction-v1 Schema 验证的强类型产物。 */
public record EvidenceBundle(
        ResourceId bundleId,
        ResourceId answerVersionId,
        String answerHash,
        com.aiinterviewcoach.domain.platform.PromptSchemaPin schemaPin,
        List<EvidenceItem> spans
) {

    public EvidenceBundle {
        DomainPreconditions.requireNonNull(bundleId, "bundleId");
        DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
        answerHash = DomainPreconditions.requireText(answerHash, "answerHash");
        DomainPreconditions.require(answerHash.matches("(?i)^[a-f0-9]{64}$"), DomainErrorCode.INVALID_ARGUMENT,
                "answerHash must be a SHA-256 hex digest");
        answerHash = answerHash.toLowerCase(Locale.ROOT);
        DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
        spans = List.copyOf(spans == null ? List.of() : spans);
        DomainPreconditions.require(spans.size() <= 64, DomainErrorCode.INVALID_ARGUMENT,
                "evidence span count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(spans.stream().map(EvidenceItem::evidenceId).toList()).size()
                        == spans.size(), DomainErrorCode.INVALID_ARGUMENT,
                "evidence IDs must be unique");
    }
}
