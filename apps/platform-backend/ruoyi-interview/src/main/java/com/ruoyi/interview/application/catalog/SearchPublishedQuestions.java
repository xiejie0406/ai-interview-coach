package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.platform.TenantId;

@FunctionalInterface
public interface SearchPublishedQuestions {

    CursorPage<PublishedQuestionSummary> handle(TenantId catalogTenantId, QuestionSearchCriteria criteria);
}
