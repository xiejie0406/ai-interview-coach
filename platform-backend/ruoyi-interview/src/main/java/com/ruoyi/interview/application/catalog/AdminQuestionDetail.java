package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.Optional;

/** Admin 工作流快照；草稿、发布版本和 Rubric 都保持不可变版本语义。 */
public record AdminQuestionDetail(
        AdminQuestionSummary question,
        Optional<QuestionVersion> draft,
        Optional<QuestionVersion> published,
        Optional<RubricVersion> rubric
) {
    public AdminQuestionDetail {
        DomainPreconditions.requireNonNull(question, "questionSummary");
        draft = draft == null ? Optional.empty() : draft;
        published = published == null ? Optional.empty() : published;
        rubric = rubric == null ? Optional.empty() : rubric;
    }
}
