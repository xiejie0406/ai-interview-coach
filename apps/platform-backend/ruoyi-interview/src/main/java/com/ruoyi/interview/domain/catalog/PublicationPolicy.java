package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

/** 发布前的确定性门禁；模型不能绕过来源、Rubric 或状态要求。 */
public final class PublicationPolicy {

    public void assertPublishable(Question question, QuestionVersion questionVersion, RubricVersion rubricVersion) {
        DomainPreconditions.requireNonNull(question, "question");
        DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
        DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
        DomainPreconditions.require(question.status() == QuestionStatus.IN_REVIEW
                        || question.status() == QuestionStatus.PUBLISHED_WITH_REVIEW,
                DomainErrorCode.QUESTION_NOT_PUBLISHABLE,
                "question must be in review before publication");
        DomainPreconditions.require(question.id().equals(questionVersion.questionId())
                        && question.tenantId().equals(questionVersion.tenantId()),
                DomainErrorCode.TENANT_MISMATCH, "question and question version do not belong together");
        DomainPreconditions.require(questionVersion.id().equals(rubricVersion.questionVersionId())
                        && questionVersion.tenantId().equals(rubricVersion.tenantId()),
                DomainErrorCode.RUBRIC_REQUIRED, "rubric does not belong to question version");
        DomainPreconditions.require(!questionVersion.answerPoints().isEmpty(),
                DomainErrorCode.QUESTION_NOT_PUBLISHABLE, "answer points are required for publication");
        DomainPreconditions.require(questionVersion.source().isPresent(),
                DomainErrorCode.CONTENT_SOURCE_REQUIRED,
                "server-verified content source evidence is required");
    }
}
