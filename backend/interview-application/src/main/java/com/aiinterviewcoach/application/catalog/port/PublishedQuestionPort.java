package com.aiinterviewcoach.application.catalog.port;

import com.aiinterviewcoach.application.catalog.PublishedQuestionSnapshot;
import com.aiinterviewcoach.application.catalog.PublishedQuestionSummary;
import com.aiinterviewcoach.application.catalog.QuestionSearchCriteria;
import com.aiinterviewcoach.application.shared.CursorPage;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

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
