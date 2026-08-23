package com.aiinterviewcoach.domain.voice;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 不可变转写正文版本；用户修正只能 append 新版本。 */
public record TranscriptVersion(
        ResourceId id,
        TenantId tenantId,
        ResourceId transcriptId,
        int versionNo,
        TranscriptSource source,
        String text,
        String contentHash,
        String language,
        String offsetUnit,
        List<ConfidenceSpan> lowConfidenceSpans,
        Optional<ResourceId> providerInvocationId,
        Optional<UserId> correctedBy,
        Optional<ResourceId> supersedesId,
        Instant createdAt
) {

    public TranscriptVersion {
        DomainPreconditions.requireNonNull(id, "transcriptVersionId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
        DomainPreconditions.require(versionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "transcript version number must be positive");
        DomainPreconditions.requireNonNull(source, "transcriptSource");
        text = DomainPreconditions.requireText(text, "transcriptText");
        contentHash = DomainPreconditions.requireText(contentHash, "transcriptContentHash");
        language = DomainPreconditions.requireText(language, "transcriptLanguage");
        offsetUnit = DomainPreconditions.requireText(offsetUnit, "transcriptOffsetUnit");
        DomainPreconditions.require(offsetUnit.equals("UTF16") || offsetUnit.equals("UNICODE_CODE_POINT"),
                DomainErrorCode.INVALID_ARGUMENT, "unsupported transcript offset unit");
        lowConfidenceSpans = List.copyOf(lowConfidenceSpans == null ? List.of() : lowConfidenceSpans);
        int maximumOffset = offsetUnit.equals("UTF16")
                ? text.length()
                : text.codePointCount(0, text.length());
        lowConfidenceSpans.forEach(span -> DomainPreconditions.require(
                span.endExclusive() <= maximumOffset, DomainErrorCode.INVALID_ARGUMENT,
                "confidence span exceeds transcript text"));
        providerInvocationId = providerInvocationId == null ? Optional.empty() : providerInvocationId;
        correctedBy = correctedBy == null ? Optional.empty() : correctedBy;
        supersedesId = supersedesId == null ? Optional.empty() : supersedesId;
        DomainPreconditions.require((source == TranscriptSource.ASR) == providerInvocationId.isPresent(),
                DomainErrorCode.INVALID_ARGUMENT, "ASR transcript requires provider invocation reference");
        DomainPreconditions.require((source == TranscriptSource.USER_CORRECTION) == correctedBy.isPresent(),
                DomainErrorCode.INVALID_ARGUMENT, "user correction requires correcting user");
        DomainPreconditions.requireNonNull(createdAt, "transcriptVersionCreatedAt");
    }

    @Override
    public String toString() {
        return "TranscriptVersion[id=" + id + ", tenantId=" + tenantId + ", transcriptId="
                + transcriptId + ", versionNo=" + versionNo + ", source=" + source
                + ", text=<redacted>, contentHash=<redacted>, language=" + language
                + ", offsetUnit=" + offsetUnit + ", lowConfidenceSpans=" + lowConfidenceSpans.size()
                + ", providerInvocationId=" + providerInvocationId + ", correctedBy=" + correctedBy
                + ", supersedesId=" + supersedesId + ", createdAt=" + createdAt + "]";
    }
}
