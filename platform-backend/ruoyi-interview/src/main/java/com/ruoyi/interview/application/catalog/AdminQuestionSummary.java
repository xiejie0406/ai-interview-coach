package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

/** Admin 题目根的只读投影；正文必须通过 {@link AdminQuestionDetail} 的版本快照读取。 */
public record AdminQuestionSummary(
        ResourceId questionId,
        String stableKey,
        QuestionStatus state,
        Optional<ImmutableVersionRef> currentDraftVersion,
        Optional<ImmutableVersionRef> currentPublishedVersion,
        AggregateVersion aggregateVersion
) {
    public AdminQuestionSummary {
        DomainPreconditions.requireNonNull(questionId, "questionId");
        stableKey = DomainPreconditions.requireText(stableKey, "stableKey");
        DomainPreconditions.requireNonNull(state, "questionState");
        currentDraftVersion = currentDraftVersion == null ? Optional.empty() : currentDraftVersion;
        currentPublishedVersion = currentPublishedVersion == null ? Optional.empty() : currentPublishedVersion;
        DomainPreconditions.requireNonNull(aggregateVersion, "aggregateVersion");
    }
}
