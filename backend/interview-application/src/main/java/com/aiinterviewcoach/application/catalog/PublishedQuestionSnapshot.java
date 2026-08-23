package com.aiinterviewcoach.application.catalog;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.List;

/** Practice/Interview/Evaluation 只读消费的发布快照，不暴露 Catalog Repository/JPA entity。 */
public record PublishedQuestionSnapshot(
        ResourceId questionId,
        ImmutableVersionRef questionVersion,
        ImmutableVersionRef rubricVersion,
        String title,
        String stem,
        List<String> answerPoints,
        List<String> followUpTemplates,
        String difficulty,
        List<String> targetRoles,
        String locale
) {

    public PublishedQuestionSnapshot {
        DomainPreconditions.requireNonNull(questionId, "questionId");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        title = DomainPreconditions.requireText(title, "questionTitle");
        stem = DomainPreconditions.requireText(stem, "questionStem");
        answerPoints = List.copyOf(DomainPreconditions.requireNonEmpty(answerPoints, "answerPoints"));
        followUpTemplates = List.copyOf(DomainPreconditions.requireNonNull(
                followUpTemplates, "followUpTemplates"));
        difficulty = DomainPreconditions.requireText(difficulty, "difficulty");
        targetRoles = List.copyOf(DomainPreconditions.requireNonEmpty(targetRoles, "targetRoles"));
        locale = DomainPreconditions.requireText(locale, "locale");
    }

    @Override
    public String toString() {
        return "PublishedQuestionSnapshot[questionId=" + questionId + ", questionVersion=" + questionVersion
                + ", rubricVersion=" + rubricVersion + ", title=<redacted>, stem=<redacted>"
                + ", answerPoints=<redacted:" + answerPoints.size() + ">, followUpTemplates=<redacted:"
                + followUpTemplates.size() + ">, difficulty=" + difficulty + ", targetRoles=" + targetRoles
                + ", locale=" + locale + "]";
    }
}
