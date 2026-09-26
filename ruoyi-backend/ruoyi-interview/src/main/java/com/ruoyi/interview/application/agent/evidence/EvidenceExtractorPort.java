package com.ruoyi.interview.application.agent.evidence;

import com.ruoyi.interview.domain.evaluation.EvidenceBundle;
import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

/** Provider-neutral Evidence Extractor；Adapter 必须先做 evidence-extraction-v1 Schema 验证。 */
public interface EvidenceExtractorPort {

    Result extract(Request request);

    record Request(
            TenantId tenantId,
            ResourceId evaluationId,
            ResourceId answerVersionId,
            String answerHash,
            PromptSchemaPin schemaPin,
            ProviderPolicySnapshot providerPolicy,
            String sourceContentRef,
            CorrelationId correlationId
    ) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            answerHash = DomainPreconditions.requireText(answerHash, "answerHash");
            DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
            DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
            sourceContentRef = DomainPreconditions.requireText(sourceContentRef, "sourceContentRef");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }

        @Override
        public String toString() {
            return "Request[tenantId=" + tenantId + ", evaluationId=" + evaluationId
                    + ", answerVersionId=" + answerVersionId + ", answerHash=" + answerHash
                    + ", schemaPin=" + schemaPin + ", providerRoute=" + providerPolicy.routePlanId()
                    + ", sourceContentRef=<redacted>]";
        }
    }

    record Result(EvidenceBundle bundle, boolean schemaValidated, String failureCode) {
        public Result {
            if (schemaValidated && bundle == null) {
                throw new IllegalArgumentException("validated evidence result requires a bundle");
            }
            if (!schemaValidated && (failureCode == null || failureCode.isBlank())) {
                throw new IllegalArgumentException("invalid evidence result requires a failureCode");
            }
        }
    }
}
