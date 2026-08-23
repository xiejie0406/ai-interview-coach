package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.RetryDisposition;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.Optional;

/** 外部 publish 失败后的权威 delivery 状态更新；只接受稳定错误码，不接受异常正文。 */
@FunctionalInterface
public interface FailOutboxEvent {

    void handle(Command command);

    record Command(
            TenantId tenantId,
            ResourceId eventId,
            String publisherId,
            AggregateVersion expectedVersion,
            String errorCode,
            RetryDisposition disposition,
            Optional<Instant> retryAt,
            Instant failedAt
    ) {
        public Command {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(eventId, "eventId");
            publisherId = DomainPreconditions.requireText(publisherId, "publisherId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            errorCode = DomainPreconditions.requireText(errorCode, "outboxErrorCode");
            DomainPreconditions.requireNonNull(disposition, "retryDisposition");
            retryAt = retryAt == null ? Optional.empty() : retryAt;
            DomainPreconditions.requireNonNull(failedAt, "failedAt");
            if (disposition.permitsAutomaticRetry()) {
                DomainPreconditions.require(retryAt.isPresent() && retryAt.orElseThrow().isAfter(failedAt),
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                        "retryable outbox failure requires a future retryAt");
            } else {
                DomainPreconditions.require(retryAt.isEmpty(),
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                        "final outbox failure cannot contain retryAt");
            }
        }
    }
}
