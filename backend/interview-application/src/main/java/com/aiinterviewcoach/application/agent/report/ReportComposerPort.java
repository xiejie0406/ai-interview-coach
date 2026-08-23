package com.aiinterviewcoach.application.agent.report;

import com.aiinterviewcoach.domain.evaluation.EvidenceBundle;
import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.evaluation.ReportComposition;
import com.aiinterviewcoach.domain.evaluation.RubricScore;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

/** Provider-neutral Report Composer；Provider 返回结构化候选，不拥有持久化正文引用。 */
public interface ReportComposerPort {

    Result compose(Request request);

    record Request(
            TenantId tenantId,
            ResourceId evaluationId,
            ResourceId evaluationVersionId,
            EvidenceBundle evidenceBundle,
            RubricScore rubricJudgement,
            PromptSchemaPin schemaPin,
            ProviderPolicySnapshot providerPolicy,
            CorrelationId correlationId
    ) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(evaluationId, "evaluationId");
            DomainPreconditions.requireNonNull(evaluationVersionId, "evaluationVersionId");
            DomainPreconditions.requireNonNull(evidenceBundle, "evidenceBundle");
            DomainPreconditions.requireNonNull(rubricJudgement, "rubricJudgement");
            DomainPreconditions.requireNonNull(schemaPin, "schemaPin");
            DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }

    record Result(ReportComposition composition, boolean schemaValidated, String failureCode) {
        public Result {
            if (schemaValidated && composition == null) {
                throw new IllegalArgumentException("validated report result requires a composition");
            }
            if (!schemaValidated && (failureCode == null || failureCode.isBlank())) {
                throw new IllegalArgumentException("invalid report result requires a failureCode");
            }
        }

        @Override
        public String toString() {
            return "Result[composition=<redacted>, schemaValidated=" + schemaValidated
                    + ", failureCode=" + failureCode + "]";
        }
    }
}
