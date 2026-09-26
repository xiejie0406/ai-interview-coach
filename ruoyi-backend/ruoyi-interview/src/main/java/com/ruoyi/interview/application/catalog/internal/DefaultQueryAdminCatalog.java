package com.ruoyi.interview.application.catalog.internal;

import com.ruoyi.interview.application.catalog.AdminQuestionDetail;
import com.ruoyi.interview.application.catalog.AdminQuestionSummary;
import com.ruoyi.interview.application.catalog.QueryAdminCatalog;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.catalog.QuestionCategory;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;
import java.util.Optional;

/** Admin 查询的应用 owner；Controller 不得绕过此用例直接访问 JDBC Repository。 */
public final class DefaultQueryAdminCatalog implements QueryAdminCatalog {

    private final CatalogRepository repository;
    private final CatalogAccess access;

    public DefaultQueryAdminCatalog(CatalogRepository repository, CatalogAccess access) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.access = java.util.Objects.requireNonNull(access);
    }

    @Override
    public CursorPage<AdminQuestionSummary> search(
            TenantId catalogTenantId,
            Optional<String> category,
            Optional<QuestionStatus> state,
            Optional<String> cursor,
            int limit,
            OperationContext context
    ) {
        access.requireReader(context);
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        Optional<String> normalizedCategory = category == null ? Optional.empty()
                : category.map(QuestionCategory::requireStored);
        return repository.searchQuestions(targetTenant, normalizedCategory, state, cursor, limit);
    }

    @Override
    public AdminQuestionDetail get(
            TenantId catalogTenantId,
            ResourceId questionId,
            OperationContext context
    ) {
        access.requireReader(context);
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        var question = repository.findQuestion(targetTenant, questionId).orElseThrow(() -> notFound(questionId));
        Optional<QuestionVersion> draft = resolveVersion(targetTenant, question.currentDraftVersion());
        Optional<QuestionVersion> published = resolveVersion(targetTenant, question.currentPublishedVersion());
        Optional<ResourceId> rubricQuestionVersion = draft.map(QuestionVersion::id)
                .or(() -> published.map(QuestionVersion::id));
        var rubric = rubricQuestionVersion.flatMap(id ->
                repository.findLatestRubricForQuestionVersion(targetTenant, id));
        return new AdminQuestionDetail(new AdminQuestionSummary(question.id(), question.stableKey(),
                question.status(), question.currentDraftVersion(), question.currentPublishedVersion(),
                question.version()), draft, published, rubric);
    }

    @Override
    public Optional<RubricVersion> getRubricVersion(
            TenantId catalogTenantId,
            ResourceId rubricVersionId,
            OperationContext context
    ) {
        access.requireReader(context);
        TenantId targetTenant = access.requireCatalogTenant(catalogTenantId);
        return repository.findRubricVersion(targetTenant, rubricVersionId);
    }

    private Optional<QuestionVersion> resolveVersion(
            TenantId tenantId,
            Optional<ImmutableVersionRef> reference
    ) {
        return reference.map(ref -> {
            QuestionVersion version = repository.findQuestionVersion(tenantId, ref.resourceId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "question points to a missing immutable version", false,
                            Map.of("questionVersionId", ref.resourceId().value())));
            if (!version.versionRef().equals(ref)) {
                throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "question immutable version reference does not match stored content", false,
                        Map.of("questionVersionId", ref.resourceId().value()));
            }
            return version;
        });
    }

    private static ApplicationException notFound(ResourceId questionId) {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND, "question was not found", false,
                Map.of("questionId", questionId.value()));
    }
}
