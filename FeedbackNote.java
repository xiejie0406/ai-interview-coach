package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 用户对 Evaluation 的 append-only 私密反馈；commentRef 指向受控正文。 */
public record FeedbackNote(
        ResourceId feedbackId,
        UserId authorUserId,
        FeedbackType type,
        Optional<String> commentRef,
        Instant createdAt
) {

    public FeedbackNote {
        DomainPreconditions.requireNonNull(feedbackId, "feedbackId");
        DomainPreconditions.requireNonNull(authorUserId, "authorUserId");
        DomainPreconditions.requireNonNull(type, "feedbackType");
        commentRef = commentRef == null ? Optional.empty() : commentRef;
        commentRef = commentRef.map(value -> DomainPreconditions.requireText(value, "commentRef"));
        DomainPreconditions.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public String toString() {
        return "FeedbackNote[feedbackId=" + feedbackId + ", authorUserId=" + authorUserId
                + ", type=" + type + ", commentRef=<redacted>, createdAt=" + createdAt + "]";
    }
}
