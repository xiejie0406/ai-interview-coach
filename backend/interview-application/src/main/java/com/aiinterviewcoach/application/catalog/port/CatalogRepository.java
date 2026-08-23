package com.aiinterviewcoach.application.catalog.port;

import com.aiinterviewcoach.domain.catalog.Question;
import com.aiinterviewcoach.domain.catalog.QuestionVersion;
import com.aiinterviewcoach.domain.catalog.QuestionPublication;
import com.aiinterviewcoach.domain.catalog.RubricVersion;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

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
