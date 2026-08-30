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
