package com.aiinterviewcoach.application.practice.port;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

/** Practice 只发布不可变 AnswerVersion 引用，不直接调用模型或 evaluation Repository。 */
public interface EvaluationRequestPort {

    OperationAccepted request(Request request);

    record Request(
            TenantId tenantId,
            ResourceId answerVersionId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion,
            ResourceId businessOperationId,
            CorrelationId correlationId
    ) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            DomainPreconditions.requireNonNull(questionVersion, "questionVersion");
            DomainPreconditions.requireNonNull(rubricVersion, "rubricVersion");
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }
}
