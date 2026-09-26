package com.ruoyi.fashion.application.agent;

public record AgentView(
        String id,
        String agentCode,
        String name,
        String agentType,
        String description,
        String currentVersionId,
        String status,
        long rowVersion) {
}
