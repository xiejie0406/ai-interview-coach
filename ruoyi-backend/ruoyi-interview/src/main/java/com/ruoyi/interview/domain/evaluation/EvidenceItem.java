package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Locale;
import java.util.Optional;

/** EvidenceExtractionV1 span；仅保存 offset 与 quote hash，不保存引用原文。 */
public record EvidenceItem(
        ResourceId evidenceId,
        EvidenceType type,
        int startInclusive,
        int endExclusive,
        String quoteHash,
        Optional<String> reasonCode
) {

    public EvidenceItem {
        DomainPreconditions.requireNonNull(evidenceId, "evidenceId");
        DomainPreconditions.requireNonNull(type, "evidenceType");
        DomainPreconditions.require(startInclusive >= 0 && endExclusive > startInclusive,
                DomainErrorCode.INVALID_ARGUMENT, "evidence offsets are invalid");
        quoteHash = DomainPreconditions.requireText(quoteHash, "quoteHash");
        DomainPreconditions.require(quoteHash.matches("(?i)^[a-f0-9]{64}$"), DomainErrorCode.INVALID_ARGUMENT,
                "quoteHash must be a SHA-256 hex digest");
        quoteHash = quoteHash.toLowerCase(Locale.ROOT);
        reasonCode = reasonCode == null ? Optional.empty() : reasonCode;
        reasonCode = reasonCode.map(value -> DomainPreconditions.requireText(value, "reasonCode"));
        reasonCode.ifPresent(value -> DomainPreconditions.require(value.length() <= 96,
                DomainErrorCode.INVALID_ARGUMENT, "reasonCode is too long"));
    }

    @Override
    public String toString() {
        return "EvidenceItem[evidenceId=" + evidenceId + ", type=" + type
                + ", startInclusive=" + startInclusive + ", endExclusive=" + endExclusive
                + ", quoteHash=<redacted>, reasonCode="
                + (reasonCode.isPresent() ? "<present>" : "<absent>") + "]";
    }
}
