package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.List;

/** 用户搜索结果只含展示元数据；答案点、Rubric 和追问模板只走内部版本化快照端口。 */
public record PublishedQuestionSummary(
        ResourceId questionId,
        ImmutableVersionRef questionVersion,
        String title,
        String category,
        String difficulty,
        List<String> targetRoles,
        String locale
) {

    public PublishedQuestionSummary {
        DomainPreconditions.requireNonNull(questionId, "questionId");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        title = DomainPreconditions.requireText(title, "questionTitle");
        category = com.ruoyi.interview.domain.catalog.QuestionCategory.requireStored(category);
        difficulty = DomainPreconditions.requireText(difficulty, "difficulty");
        targetRoles = List.copyOf(DomainPreconditions.requireNonEmpty(targetRoles, "targetRoles"));
        locale = DomainPreconditions.requireText(locale, "locale");
    }

    @Override
    public String toString() {
        return "PublishedQuestionSummary[questionId=" + questionId + ", questionVersion=" + questionVersion
                + ", title=<redacted>, category=" + category + ", difficulty=" + difficulty + ", targetRoles=" + targetRoles
                + ", locale=" + locale + "]";
    }
}
