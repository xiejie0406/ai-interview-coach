package com.ruoyi.interview.application.agent.report;

import com.ruoyi.interview.domain.evaluation.EvidenceBundle;
import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.evaluation.ReportComposition;
import com.ruoyi.interview.domain.evaluation.RubricScore;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

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
