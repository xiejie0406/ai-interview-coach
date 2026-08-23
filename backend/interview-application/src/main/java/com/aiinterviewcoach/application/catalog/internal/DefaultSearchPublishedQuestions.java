package com.aiinterviewcoach.application.catalog.internal;

import com.aiinterviewcoach.application.catalog.PublishedQuestionSummary;
import com.aiinterviewcoach.application.catalog.QuestionSearchCriteria;
import com.aiinterviewcoach.application.catalog.SearchPublishedQuestions;
import com.aiinterviewcoach.application.catalog.port.PublishedQuestionPort;
import com.aiinterviewcoach.application.shared.CursorPage;
import com.aiinterviewcoach.domain.platform.TenantId;

/** 只读搜索委托给版本化公开端口；不向调用者暴露 Repository。 */
public final class DefaultSearchPublishedQuestions implements SearchPublishedQuestions {

    private final PublishedQuestionPort publishedQuestions;

    public DefaultSearchPublishedQuestions(PublishedQuestionPort publishedQuestions) {
        this.publishedQuestions = java.util.Objects.requireNonNull(publishedQuestions);
    }

    @Override
    public CursorPage<PublishedQuestionSummary> handle(TenantId catalogTenantId, QuestionSearchCriteria criteria) {
        return publishedQuestions.searchPublished(catalogTenantId, criteria);
    }
}
