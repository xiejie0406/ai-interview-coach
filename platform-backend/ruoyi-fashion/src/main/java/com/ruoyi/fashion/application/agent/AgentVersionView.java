package com.ruoyi.fashion.application.agent;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record AgentVersionView(
        String id,
        String agentId,
        int versionNo,
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
        int timeoutSeconds,
        String configHash,
        String status,
        String publishedBy,
        Instant publishedAt,
        long rowVersion) {
}
