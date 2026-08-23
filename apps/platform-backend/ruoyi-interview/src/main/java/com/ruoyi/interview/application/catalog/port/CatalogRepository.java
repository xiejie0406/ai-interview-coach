package com.ruoyi.interview.application.catalog.port;

import com.ruoyi.interview.domain.catalog.Question;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.QuestionPublication;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

public interface CatalogRepository {

    Optional<Question> findQuestion(TenantId tenantId, ResourceId questionId);

    Optional<Question> findByStableKey(TenantId tenantId, String stableKey);

    Optional<QuestionVersion> findQuestionVersion(TenantId tenantId, ResourceId questionVersionId);

    Optional<RubricVersion> findRubricVersion(TenantId tenantId, ResourceId rubricVersionId);

    void saveQuestion(Question question);

    void appendQuestionVersion(QuestionVersion version);

    void appendRubricVersion(RubricVersion version);

    void appendPublication(QuestionPublication publication);
}
