package com.ruoyi.interview.application.agent.interview;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;

/** Interview Agent 配置版本解析；客户端只能提交/携带不可变版本引用，不能提交 Prompt/Provider。 */
public interface InterviewAgentConfigurationPort {

    Configuration resolve(ImmutableVersionRef configVersion);

    record Configuration(PromptSchemaPin promptSchemaPin, ProviderPolicySnapshot providerPolicy) {
        public Configuration {
            DomainPreconditions.requireNonNull(promptSchemaPin, "promptSchemaPin");
            DomainPreconditions.requireNonNull(providerPolicy, "providerPolicy");
        }
    }
}
