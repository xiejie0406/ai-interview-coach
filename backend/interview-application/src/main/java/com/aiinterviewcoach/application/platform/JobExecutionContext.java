package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;

/**
 * 原子 claim 后生成的服务端内部执行凭据。业务回写仍必须在同一事务中对权威 Job 重新校验此上下文。
 */
public record JobExecutionContext(
        TenantId tenantId,
        ResourceId jobId,
        String workerId,
        int attemptNo,
        AggregateVersion expectedJobVersion,
        Instant leaseExpiresAt,
        CorrelationId correlationId
) {

    public JobExecutionContext {
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(jobId, "jobId");
        workerId = DomainPreconditions.requireText(workerId, "workerId");
        DomainPreconditions.require(attemptNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "attempt number must be positive");
        DomainPreconditions.requireNonNull(expectedJobVersion, "expectedJobVersion");
        DomainPreconditions.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
    }

    public void requireLocallyUnexpired(Instant at) {
        DomainPreconditions.requireNonNull(at, "executionCheckAt");
        DomainPreconditions.require(at.isBefore(leaseExpiresAt), DomainErrorCode.JOB_LEASE_NOT_HELD,
                "job execution lease has expired");
    }
}
