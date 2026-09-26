package com.ruoyi.interview.application.catalog.port;

import com.ruoyi.interview.application.catalog.AdminQuestionSummary;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.catalog.Question;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.QuestionPublication;
import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

public interface CatalogRepository {

    Optional<Question> findQuestion(TenantId tenantId, ResourceId questionId);

    Optional<Question> findByStableKey(TenantId tenantId, String stableKey);

    Optional<QuestionVersion> findQuestionVersion(TenantId tenantId, ResourceId questionVersionId);

    Optional<RubricVersion> findRubricVersion(TenantId tenantId, ResourceId rubricVersionId);

    Optional<RubricVersion> findLatestRubricForQuestionVersion(
            TenantId tenantId,
            ResourceId questionVersionId
    );

    CursorPage<AdminQuestionSummary> searchQuestions(
            TenantId tenantId,
            Optional<String> category,
            Optional<QuestionStatus> state,
            Optional<String> cursor,
            int limit
    );

    void saveQuestion(Question question);

    void appendQuestionVersion(QuestionVersion version);

    void appendRubricVersion(RubricVersion version);

    void appendPublication(QuestionPublication publication);
}
