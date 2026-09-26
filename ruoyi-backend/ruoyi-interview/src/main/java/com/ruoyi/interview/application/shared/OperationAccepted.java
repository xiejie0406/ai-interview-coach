package com.ruoyi.interview.application.shared;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Optional;

/** 异步受理结果；存在该对象只表示 durable 受理，不表示 Job 或业务功能完成。 */
public record OperationAccepted(
        ResourceId operationId,
        Optional<ResourceId> jobId,
        Optional<ResourceId> resourceId,
        String statusPath,
        Optional<String> streamPath,
        Instant acceptedAt
) {

    public OperationAccepted {
        DomainPreconditions.requireNonNull(operationId, "operationId");
        jobId = jobId == null ? Optional.empty() : jobId;
        resourceId = resourceId == null ? Optional.empty() : resourceId;
        statusPath = DomainPreconditions.requireText(statusPath, "statusPath");
        streamPath = streamPath == null ? Optional.empty() : streamPath;
        streamPath.ifPresent(path -> DomainPreconditions.requireText(path, "streamPath"));
        DomainPreconditions.requireNonNull(acceptedAt, "acceptedAt");
    }
}
