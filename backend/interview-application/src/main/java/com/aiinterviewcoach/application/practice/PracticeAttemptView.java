package com.aiinterviewcoach.application.practice;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.practice.PracticeAttemptState;

import java.time.Instant;
import java.util.Optional;

public record PracticeAttemptView(
        ResourceId attemptId,
        ImmutableVersionRef questionVersion,
        ImmutableVersionRef rubricVersion,
        PracticeAttemptState state,
        AggregateVersion version,
        Optional<ResourceId> latestAnswerVersionId,
        Optional<Instant> submittedAt
) {

    public PracticeAttemptView {
        DomainPreconditions.requireNonNull(attemptId, "attemptId");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        DomainPreconditions.requireNonNull(state, "attemptState");
        DomainPreconditions.requireNonNull(version, "attemptVersion");
        latestAnswerVersionId = latestAnswerVersionId == null ? Optional.empty() : latestAnswerVersionId;
        submittedAt = submittedAt == null ? Optional.empty() : submittedAt;
    }
}
