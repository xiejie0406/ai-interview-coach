package com.ruoyi.fashion.controller.rest.agent;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.agent.AgentVersionDraft;

public class AgentVersionCreateRequest {
    public String providerCode;
    public String modelName;
    public String systemInstruction;
    public JsonNode modelConfig;
    public JsonNode tools;
    public JsonNode handoffs;
    public JsonNode inputSchema;
    public JsonNode outputSchema;
    public JsonNode guardrails;
    public int maxSteps;
    public int timeoutSeconds;

    AgentVersionDraft toCommand() {
        return new AgentVersionDraft(providerCode, modelName, systemInstruction, modelConfig, tools, handoffs,
                inputSchema, outputSchema, guardrails, maxSteps, timeoutSeconds);
    }
}
