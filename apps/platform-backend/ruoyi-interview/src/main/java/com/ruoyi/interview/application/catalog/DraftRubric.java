package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.catalog.RubricDimension;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.List;

/** Rubric 以新不可变版本追加；不能覆盖已发布版本。 */
@FunctionalInterface
public interface DraftRubric {

    Result handle(Command command);

    record Command(
            ResourceId questionVersionId,
            List<RubricDimension> dimensions,
            String refusalPolicy,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(questionVersionId, "questionVersionId");
            dimensions = List.copyOf(DomainPreconditions.requireNonEmpty(dimensions, "rubricDimensions"));
            refusalPolicy = DomainPreconditions.requireText(refusalPolicy, "refusalPolicy");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[questionVersionId=" + questionVersionId + ", dimensions=<redacted:"
                    + dimensions.size() + ">, refusalPolicy=<redacted>, context=" + context + "]";
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
