package com.ruoyi.interview.application.practice;

import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.practice.PracticeAttemptState;

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
