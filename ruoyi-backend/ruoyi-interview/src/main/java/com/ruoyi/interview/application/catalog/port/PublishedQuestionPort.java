package com.ruoyi.interview.application.catalog.port;

import com.ruoyi.interview.application.catalog.PublishedQuestionSnapshot;
import com.ruoyi.interview.application.catalog.PublishedQuestionSummary;
import com.ruoyi.interview.application.catalog.QuestionSearchCriteria;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

/** Catalog 对其他域唯一公开的版本化只读端口。 */
public interface PublishedQuestionPort {

    /** 仅按已发布题目与 Rubric 的完整版本组合读取评分正文；默认拒绝未接入的实现。 */
    default Optional<com.ruoyi.interview.domain.catalog.RubricVersion> findPublishedRubric(
            TenantId tenantId, ImmutableVersionRef questionVersion, ImmutableVersionRef rubricVersion) {
        return Optional.empty();
    }

    Optional<PublishedQuestionSnapshot> findPublished(TenantId tenantId, ResourceId questionId);

    Optional<PublishedQuestionSnapshot> findPublishedVersion(
            TenantId tenantId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion
    );

    CursorPage<PublishedQuestionSummary> searchPublished(
            TenantId tenantId,
            QuestionSearchCriteria criteria
    );
}
