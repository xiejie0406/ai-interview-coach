package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.catalog.RubricDimension;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.List;

/** Rubric 以新不可变版本追加；不能覆盖已发布版本。 */
@FunctionalInterface
public interface DraftRubric {

    /** catalog tenant 由服务端配置传入；请求体不得携带租户边界。 */
    Result handle(TenantId catalogTenantId, Command command);

    record Command(
            ResourceId questionId,
            ResourceId questionVersionId,
            List<RubricDimension> dimensions,
            String refusalPolicy,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            dimensions = List.copyOf(DomainPreconditions.requireNonEmpty(dimensions, "rubricDimensions"));
            refusalPolicy = DomainPreconditions.requireText(refusalPolicy, "refusalPolicy");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[questionId=" + questionId + ", questionVersionId=" + questionVersionId + ", dimensions=<redacted:"
                    + dimensions.size() + ">, refusalPolicy=<redacted>, expectedVersion=" + expectedVersion
                    + ", context=" + context + "]";
        }
    }

    record Result(ResourceId rubricVersionId, int versionNo) {
        public Result {
            DomainPreconditions.requireNonNull(rubricVersionId, "rubricVersionId");
            DomainPreconditions.require(versionNo > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "rubric version number must be positive");
        }
    }
}
