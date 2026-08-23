package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class OperationAcceptedFactory {

    private OperationAcceptedFactory() {
    }

    static OperationAccepted forJob(ResourceId jobId, ResourceId sessionId, Instant acceptedAt) {
        return new OperationAccepted(jobId, Optional.of(jobId), Optional.of(sessionId),
                "/api/v1/jobs/" + jobId.value(),
                Optional.of("/api/v1/streams/interviews/" + sessionId.value()),
                acceptedAt);
    }

    static Map<String, String> idempotencyReferences(OperationAccepted accepted, Map<String, String> additional) {
        LinkedHashMap<String, String> references = new LinkedHashMap<>(additional);
        references.put("operationId", accepted.operationId().value());
        accepted.jobId().ifPresent(value -> references.put("jobId", value.value()));
        accepted.resourceId().ifPresent(value -> references.put("operationResourceId", value.value()));
        references.put("statusPath", accepted.statusPath());
        accepted.streamPath().ifPresent(value -> references.put("streamPath", value));
        references.put("acceptedAt", accepted.acceptedAt().toString());
        return Map.copyOf(references);
    }

    static OperationAccepted replay(Map<String, String> references) {
        String operationId = required(references, "operationId");
        String statusPath = required(references, "statusPath");
        String acceptedAt = required(references, "acceptedAt");
        return new OperationAccepted(ResourceId.of(operationId),
                Optional.ofNullable(references.get("jobId")).map(ResourceId::of),
                Optional.ofNullable(references.get("operationResourceId")).map(ResourceId::of),
                statusPath, Optional.ofNullable(references.get("streamPath")), Instant.parse(acceptedAt));
    }

    private static String required(Map<String, String> references, String key) {
        String value = references.get(key);
        if (value == null || value.isBlank()) {
            throw new com.aiinterviewcoach.application.shared.ApplicationException(
                    com.aiinterviewcoach.application.shared.ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "idempotency replay is missing an operation reference", false, Map.of("referenceKey", key));
        }
        return value;
    }
}
