package com.aiinterviewcoach.application.catalog;

import com.aiinterviewcoach.application.shared.CursorPage;
import com.aiinterviewcoach.domain.platform.TenantId;

@FunctionalInterface
public interface SearchPublishedQuestions {

    CursorPage<PublishedQuestionSummary> handle(TenantId catalogTenantId, QuestionSearchCriteria criteria);
}
