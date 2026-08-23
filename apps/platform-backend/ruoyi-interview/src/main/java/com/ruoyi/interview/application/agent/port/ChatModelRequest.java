package com.ruoyi.interview.application.agent.port;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.PromptRef;
import com.ruoyi.interview.domain.platform.ProviderConfigRef;
import com.ruoyi.interview.domain.platform.SchemaRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prompt、Schema、路由配置全部显式版本化的 Provider-neutral 请求。 */
public record ChatModelRequest(
        PromptRef promptRef,
        SchemaRef schemaRef,
        ProviderConfigRef providerConfigRef,
        List<ModelMessage> messages,
        Map<String, String> parameters
) {

    public ChatModelRequest {
        DomainPreconditions.requireNonNull(promptRef, "promptRef");
        DomainPreconditions.requireNonNull(schemaRef, "schemaRef");
        DomainPreconditions.requireNonNull(providerConfigRef, "providerConfigRef");
        messages = List.copyOf(DomainPreconditions.requireNonEmpty(messages, "modelMessages"));
        parameters = Map.copyOf(new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
        parameters.forEach((key, value) -> {
            DomainPreconditions.requireText(key, "model parameter key");
            DomainPreconditions.requireText(value, "model parameter value");
        });
    }

    @Override
    public String toString() {
        return "ChatModelRequest[promptRef=" + promptRef + ", schemaRef=" + schemaRef
                + ", providerConfigRef=" + providerConfigRef + ", messages=<redacted:" + messages.size()
                + ">, parameters=" + parameters.keySet() + "]";
    }
}
