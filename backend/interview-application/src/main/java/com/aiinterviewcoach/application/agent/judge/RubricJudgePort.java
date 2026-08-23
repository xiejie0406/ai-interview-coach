package com.aiinterviewcoach.application.agent.judge;

import com.aiinterviewcoach.domain.evaluation.EvidenceBundle;
import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.evaluation.RubricScore;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

/** Provider-neutral Rubric Judge；结果是分类 judgement，不是数值总分。 */
public interface RubricJudgePort {

    Result judge(Request request);

    record Request(
            TenantId tenantId,
            ResourceId evaluationId,
            EvidenceBundle evidenceBundle,
            PromptSchemaPin schemaPin,
            ProviderPolicySnapshot providerPolicy,
            String rubricVersionId,
            CorrelationId correlationId
    ) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(evidenceBundle, "evidenceBundle");
            DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
            DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
            rubricVersionId = DomainPreconditions.requireText(rubricVersionId, "rubricVersionId");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }

    record Result(RubricScore judgement, boolean schemaValidated, String failureCode) {
        public Result {
            if (schemaValidated && judgement == null) {
                throw new IllegalArgumentException("validated rubric result requires a judgement");
            }
            if (!schemaValidated && (failureCode == null || failureCode.isBlank())) {
                throw new IllegalArgumentException("invalid rubric result requires a failureCode");
            }
        }
    }
}
