package com.ruoyi.interview.application.voice;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.voice.ConfidenceSpan;
import com.ruoyi.interview.domain.voice.TranscriptSource;
import com.ruoyi.interview.domain.voice.TranscriptState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 刷新恢复所需的 owner-scoped 转写快照；正文不会进入日志或事件。 */
@FunctionalInterface
public interface GetTranscript {

    View handle(Query query);

    record Query(ResourceId transcriptId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record View(ResourceId id, TranscriptState state, ResourceId sessionId,
                ResourceId turnId, ResourceId audioArtifactId,
                Optional<VersionView> latestVersion,
                Optional<ResourceId> confirmedVersionId,
                AggregateVersion version) {
        public View {
            DomainPreconditions.requireNonNull(id, "transcriptId");
            DomainPreconditions.requireNonNull(state, "transcriptState");
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            DomainPreconditions.requireNonNull(audioArtifactId, "audioArtifactId");
            latestVersion = latestVersion == null ? Optional.empty() : latestVersion;
            confirmedVersionId = confirmedVersionId == null ? Optional.empty() : confirmedVersionId;
            DomainPreconditions.requireNonNull(version, "transcriptAggregateVersion");
        }

        @Override
        public String toString() {
            return "View[id=" + id + ", state=" + state + ", sessionId=" + sessionId
                    + ", turnId=" + turnId + ", audioArtifactId=" + audioArtifactId
                    + ", latestVersion=<redacted>, confirmedVersionId=" + confirmedVersionId
                    + ", version=" + version + "]";
        }
    }

    record VersionView(ResourceId id, int versionNo, TranscriptSource source,
                       String text, String language, String offsetUnit,
                       List<ConfidenceSpan> lowConfidenceSpans, Instant createdAt) {
        public VersionView {
            DomainPreconditions.requireNonNull(id, "transcriptVersionId");
            DomainPreconditions.require(versionNo > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "transcript version number must be positive");
            DomainPreconditions.requireNonNull(source, "transcriptSource");
            text = DomainPreconditions.requireText(text, "transcriptText");
            language = DomainPreconditions.requireText(language, "transcriptLanguage");
            offsetUnit = DomainPreconditions.requireText(offsetUnit, "transcriptOffsetUnit");
            lowConfidenceSpans = List.copyOf(lowConfidenceSpans == null
                    ? List.of() : lowConfidenceSpans);
            DomainPreconditions.requireNonNull(createdAt, "transcriptVersionCreatedAt");
        }

        @Override
        public String toString() {
            return "VersionView[id=" + id + ", versionNo=" + versionNo + ", source=" + source
                    + ", text=<redacted>, language=" + language + ", offsetUnit=" + offsetUnit
                    + ", lowConfidenceSpans=" + lowConfidenceSpans.size() + ", createdAt="
                    + createdAt + "]";
        }
    }
}
