package com.ruoyi.fashion.application.agent.run;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record RunView(
        String id,
        String runNo,
        String conversationId,
        String agentVersionId,
        String quoteId,
        long quoteRowVersion,
        String requestKey,
        String agentConfigHash,
        Instant deadlineAt,
        JsonNode contextSnapshot,
        String outputType,
        JsonNode output,
        String outputHash,
        JsonNode validation,
        String applyStatus,
        String status,
        int currentStepNo,
        int runAttempt,
        String executionLeaseId,
        String leaseOwner,
        long fencingToken,
        Instant nextRetryAt,
        Instant leaseUntil,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage,
        long rowVersion) {
}
