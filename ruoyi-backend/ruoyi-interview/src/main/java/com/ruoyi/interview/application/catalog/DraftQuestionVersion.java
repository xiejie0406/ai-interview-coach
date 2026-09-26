package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.catalog.QuestionCategory;

import java.util.List;
import java.util.Optional;

/** 为已有题目追加不可变内容版本，并原子推进当前草稿指针。 */
@FunctionalInterface
public interface DraftQuestionVersion {

    /** catalog tenant 由服务端配置传入；主体租户只用于 RBAC 与审计。 */
    Result handle(TenantId catalogTenantId, Command command);

    record Command(
            ResourceId questionId,
            String category,
            String title,
            String stem,
            List<String> answerPoints,
            List<String> misconceptions,
            List<String> followUpTemplates,
            String difficulty,
            List<String> targetRoles,
            String locale,
            Optional<ResourceId> contentSourceVersionId,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            category = QuestionCategory.requireNew(category);
            title = DomainPreconditions.requireText(title, "title");
            stem = DomainPreconditions.requireText(stem, "stem");
            answerPoints = List.copyOf(DomainPreconditions.requireNonNull(answerPoints, "answerPoints"));
            misconceptions = List.copyOf(DomainPreconditions.requireNonNull(misconceptions, "misconceptions"));
            followUpTemplates = List.copyOf(DomainPreconditions.requireNonNull(
                    followUpTemplates, "followUpTemplates"));
            difficulty = DomainPreconditions.requireText(difficulty, "difficulty");
            targetRoles = List.copyOf(DomainPreconditions.requireNonEmpty(targetRoles, "targetRoles"));
            locale = DomainPreconditions.requireText(locale, "locale");
            contentSourceVersionId = contentSourceVersionId == null
                    ? Optional.empty() : contentSourceVersionId;
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[questionId=" + questionId + ", category=" + category + ", title=<redacted>, stem=<redacted>, answerPoints=<redacted:"
                    + answerPoints.size() + ">, misconceptions=<redacted:" + misconceptions.size()
                    + ">, followUpTemplates=<redacted:" + followUpTemplates.size() + ">, difficulty="
                    + difficulty + ", targetRoles=" + targetRoles + ", locale=" + locale
                    + ", contentSourceVersionId=" + contentSourceVersionId + ", expectedVersion="
                    + expectedVersion + ", context=" + context + "]";
        }
    }

    record Result(ResourceId questionId, ResourceId questionVersionId, AggregateVersion aggregateVersion) {
        public Result {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            DomainPreconditions.requireNonNull(aggregateVersion, "aggregateVersion");
        }
    }
}
