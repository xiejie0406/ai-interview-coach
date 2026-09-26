package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

@FunctionalInterface
public interface SubmitQuestionForReview {

    Result handle(TenantId catalogTenantId, Command command);

    record Command(ResourceId questionId, AggregateVersion expectedVersion, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(ResourceId questionId, AggregateVersion version) {
        public Result {
            DomainPreconditions.requireNonNull(questionId, "questionId");
            DomainPreconditions.requireNonNull(version, "questionVersion");
        }
    }
}
