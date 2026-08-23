package com.aiinterviewcoach.adapters.outbound.agent.config;

import com.aiinterviewcoach.adapters.outbound.AdapterUnavailableException;
import com.aiinterviewcoach.application.agent.port.PromptSchemaRegistryPort;
import com.aiinterviewcoach.domain.platform.PromptSchemaPin;

import java.util.Map;

/** 由 Boot 从已校验版本清单构造；源码不内置 Prompt/Schema id、hash 或正文。 */
public final class ConfiguredPromptSchemaRegistryAdapter implements PromptSchemaRegistryPort {

    private final Map<PromptSchemaPin, Resolved> registrations;

    public ConfiguredPromptSchemaRegistryAdapter(Map<PromptSchemaPin, Resolved> registrations) {
        this.registrations = Map.copyOf(registrations == null ? Map.of() : registrations);
    }

    @Override
    public Resolved resolve(PromptSchemaPin pin) {
        java.util.Objects.requireNonNull(pin, "promptSchemaPin");
        Resolved resolved = registrations.get(pin);
        if (resolved == null) {
            throw new AdapterUnavailableException("prompt-schema-registration");
        }
        return resolved;
    }
}
