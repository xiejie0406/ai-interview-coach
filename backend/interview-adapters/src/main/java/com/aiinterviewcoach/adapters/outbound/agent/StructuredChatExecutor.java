package com.aiinterviewcoach.adapters.outbound.agent;

import com.aiinterviewcoach.application.agent.ProviderRoutePlan;
import com.aiinterviewcoach.application.agent.port.ChatModelPort;
import com.aiinterviewcoach.application.agent.port.ChatModelRequest;
import com.aiinterviewcoach.application.agent.port.ChatModelResult;
import com.aiinterviewcoach.application.agent.port.InvocationContext;
import com.aiinterviewcoach.application.agent.port.ModelMessage;
import com.aiinterviewcoach.application.agent.port.PromptSchemaRegistryPort;
import com.aiinterviewcoach.application.agent.port.ProviderRouteRegistryPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.ProviderConfigRef;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 执行批准的主备 route；不重试同一配置，不在 adapter 内 sleep。 */
final class StructuredChatExecutor {

    private final ChatModelPort model;
    private final PromptSchemaRegistryPort promptSchemas;
    private final ProviderRouteRegistryPort providerRoutes;

    StructuredChatExecutor(
            ChatModelPort model,
            PromptSchemaRegistryPort promptSchemas,
            ProviderRouteRegistryPort providerRoutes
    ) {
        this.model = java.util.Objects.requireNonNull(model);
        this.promptSchemas = java.util.Objects.requireNonNull(promptSchemas);
        this.providerRoutes = java.util.Objects.requireNonNull(providerRoutes);
    }

    ChatModelResult.Success execute(
            String capability,
            PromptSchemaPin pin,
            ProviderPolicySnapshot policy,
            List<ModelMessage> messages,
            Map<String, String> parameters,
            InvocationContext context
    ) {
        PromptSchemaRegistryPort.Resolved promptSchema = promptSchemas.resolve(pin);
        ProviderRoutePlan route = providerRoutes.resolve(capability, policy);
        ProviderConfigRef current = route.primary();
        Set<ProviderConfigRef> attempted = new HashSet<>();
        while (true) {
            attempted.add(current);
            ChatModelResult result = model.execute(new ChatModelRequest(
                    promptSchema.promptRef(), promptSchema.schemaRef(), current, messages, parameters), context);
            if (result instanceof ChatModelResult.Success success) {
                return success;
            }
            var failure = ((ChatModelResult.Failure) result).failure();
            var next = route.next(current, failure, attempted);
            if (next.isPresent()) {
                current = next.orElseThrow();
                continue;
            }
            if ("CAPABILITY_NOT_CONFIGURED".equals(failure.errorClass())) {
                throw new ApplicationException(ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                        "chat model capability is not configured", false,
                        Map.of("capability", capability, "reasonCode", "CAPABILITY_NOT_CONFIGURED"));
            }
            boolean retryable = failure.retryDisposition().permitsAutomaticRetry();
            throw new ApplicationException(
                    retryable ? ApplicationErrorCode.CAPABILITY_UNAVAILABLE
                            : ApplicationErrorCode.PROVIDER_BAD_RESPONSE,
                    "approved provider route did not produce a structured result", retryable,
                    Map.of("capability", capability, "reasonCode", "PROVIDER_ROUTE_EXHAUSTED"));
        }
    }
}
