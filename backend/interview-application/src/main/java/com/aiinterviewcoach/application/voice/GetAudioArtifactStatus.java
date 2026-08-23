package com.aiinterviewcoach.application.voice;

import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.voice.AudioArtifactState;
import com.aiinterviewcoach.domain.voice.AudioPurpose;

import java.time.Instant;
import java.util.Optional;

@FunctionalInterface
public interface GetAudioArtifactStatus {

    View handle(Query query);

    record Query(ResourceId artifactId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record View(ResourceId id, AudioArtifactState state, AudioPurpose purpose,
                Instant expiresAt, Optional<String> codec, Optional<Long> bytes,
                Optional<Long> durationMillis, Optional<String> failureCode,
                Optional<String> deleteStatus, AggregateVersion version) {
        public View {
            DomainPreconditions.requireNonNull(id, "artifactId");
            DomainPreconditions.requireNonNull(state, "audioArtifactState");
            DomainPreconditions.requireNonNull(purpose, "audioPurpose");
            DomainPreconditions.requireNonNull(expiresAt, "audioArtifactExpiresAt");
            codec = codec == null ? Optional.empty() : codec;
            bytes = bytes == null ? Optional.empty() : bytes;
            durationMillis = durationMillis == null ? Optional.empty() : durationMillis;
            failureCode = failureCode == null ? Optional.empty() : failureCode;
            deleteStatus = deleteStatus == null ? Optional.empty() : deleteStatus;
            DomainPreconditions.requireNonNull(version, "audioArtifactVersion");
        }
    }
}
