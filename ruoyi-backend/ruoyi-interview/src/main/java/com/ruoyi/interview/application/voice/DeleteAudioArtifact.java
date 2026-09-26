package com.ruoyi.interview.application.voice;

import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.voice.AudioArtifactState;

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
