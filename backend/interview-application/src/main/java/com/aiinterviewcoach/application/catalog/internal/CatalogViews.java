package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.catalog.PublishedQuestionSnapshot;
import com.aiinterviewcoach.domain.catalog.QuestionVersion;
import com.aiinterviewcoach.domain.catalog.RubricVersion;

/** 领域版本到跨域只读快照的单向映射。 */
final class CatalogViews {

    private CatalogViews() {
    }

    static PublishedQuestionSnapshot snapshot(QuestionVersion question, RubricVersion rubric) {
        return new PublishedQuestionSnapshot(question.questionId(), question.versionRef(), rubric.versionRef(),
                question.title(), question.stem(), question.answerPoints(), question.followUpTemplates(),
                question.difficulty(), question.targetRoles(), question.locale());
    }
}
