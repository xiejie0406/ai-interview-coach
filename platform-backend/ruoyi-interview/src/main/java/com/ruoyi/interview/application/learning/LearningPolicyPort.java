package com.ruoyi.interview.application.learning;

import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

/** 服务端选择并固定 Learning Coach Prompt/Schema/Provider route；前端不能提交。 */
public interface LearningPolicyPort {
    PolicySnapshot resolve(TenantId tenantId, ResourceId reportVersionId);

    record PolicySnapshot(String configVersionId, PromptSchemaPin coachPin,
                          ProviderPolicySnapshot providerPolicy) {
        public PolicySnapshot {
            configVersionId = com.ruoyi.interview.domain.platform.DomainPreconditions.requireText(
                    configVersionId, "configVersionId");
            com.ruoyi.interview.domain.platform.DomainPreconditions.require(configVersionId.length() <= 128,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "configVersionId is too long");
            java.util.Objects.requireNonNull(coachPin);
            java.util.Objects.requireNonNull(providerPolicy);
        }
    }
}
