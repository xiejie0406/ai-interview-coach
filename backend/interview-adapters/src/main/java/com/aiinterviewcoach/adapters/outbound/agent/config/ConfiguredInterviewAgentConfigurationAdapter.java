package com.aiinterviewcoach.adapters.outbound.agent.config;

import com.aiinterviewcoach.adapters.outbound.AdapterUnavailableException;
import com.aiinterviewcoach.application.agent.interview.InterviewAgentConfigurationPort;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;

import java.util.Map;

/** Interview Agent 配置版本 registry；缺项不回退到未版本化 Prompt。 */
public final class ConfiguredInterviewAgentConfigurationAdapter implements InterviewAgentConfigurationPort {

    private final Map<ImmutableVersionRef, Configuration> configurations;

    public ConfiguredInterviewAgentConfigurationAdapter(Map<ImmutableVersionRef, Configuration> configurations) {
        this.configurations = Map.copyOf(configurations == null ? Map.of() : configurations);
    }

    @Override
    public Configuration resolve(ImmutableVersionRef configVersion) {
        java.util.Objects.requireNonNull(configVersion, "agentConfigVersion");
        Configuration configuration = configurations.get(configVersion);
        if (configuration == null) {
            throw new AdapterUnavailableException("interview-agent-configuration");
        }
        return configuration;
    }
}
