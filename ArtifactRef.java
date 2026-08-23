package com.aiinterviewcoach.domain.platform;

import java.time.Instant;

/**
 * 对受控 Artifact 的引用；普通 DTO 不应暴露对象存储 key。
 */
public record ArtifactRef(ResourceId artifactId, String purpose, DataClassification classification, Instant expiresAt) {

    public ArtifactRef {
        DomainPreconditions.requireNonNull(artifactId, "artifactId");
        purpose = DomainPreconditions.requireText(purpose, "artifactPurpose");
        DomainPreconditions.requireNonNull(classification, "classification");
        DomainPreconditions.requireNonNull(expiresAt, "artifactExpiresAt");
    }
}
