package com.aiinterviewcoach.application.voice;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.voice.AudioArtifactState;

import java.time.Instant;

@FunctionalInterface
public interface DeleteAudioArtifact {

    Result handle(Command command);

    record Command(TenantId tenantId, ResourceId artifactId, AggregateVersion expectedVersion,
                   CorrelationId correlationId, Instant requestedAt) {
        public Command {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
            DomainPreconditions.requireNonNull(requestedAt, "requestedAt");
        }
    }

    record Result(ResourceId artifactId, AudioArtifactState state,
                  AggregateVersion version, boolean externalDeleteAttempted) {
        public Result {
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            DomainPreconditions.requireNonNull(state, "audioArtifactState");
            DomainPreconditions.requireNonNull(version, "audioArtifactVersion");
        }
    }
}
