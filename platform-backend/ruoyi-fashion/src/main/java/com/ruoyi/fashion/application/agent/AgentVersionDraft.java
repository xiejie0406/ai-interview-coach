package com.ruoyi.fashion.application.agent;

import tools.jackson.databind.JsonNode;

public record AgentVersionDraft(
        String providerCode,
        String modelName,
        String systemInstruction,
        JsonNode modelConfig,
        JsonNode tools,
        JsonNode handoffs,
        JsonNode inputSchema,
        JsonNode outputSchema,
        JsonNode guardrails,
        int maxSteps,
        int timeoutSeconds) {
}
