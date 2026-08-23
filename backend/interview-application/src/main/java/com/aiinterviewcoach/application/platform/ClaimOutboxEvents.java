package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Publisher 通过本用例原子 claim；传输必须在事务外，完成后再调用 publish/fail 用例。 */
@FunctionalInterface
public interface ClaimOutboxEvents {

    List<ClaimedEvent> handle(Command command);

    record Command(String publisherId, Duration leaseDuration, int limit, Instant now) {
        public Command {
            publisherId = DomainPreconditions.requireText(publisherId, "publisherId");
            DomainPreconditions.requireNonNull(leaseDuration, "outboxLeaseDuration");
            DomainPreconditions.require(!leaseDuration.isNegative() && !leaseDuration.isZero(),
                    DomainErrorCode.INVALID_ARGUMENT, "outbox lease duration must be positive");
            DomainPreconditions.require(limit > 0 && limit <= 100, DomainErrorCode.INVALID_ARGUMENT,
                    "outbox claim limit must be between 1 and 100");
            DomainPreconditions.requireNonNull(now, "outboxClaimedAt");
        }
    }

    record ClaimedEvent(
            TenantId tenantId,
            ResourceId eventId,
            String aggregateType,
            ResourceId aggregateId,
            AggregateVersion sourceAggregateVersion,
            String eventType,
            int schemaVersion,
            CorrelationId correlationId,
            Map<String, String> payloadReferences,
            int attemptNo,
            AggregateVersion deliveryVersion,
            Instant claimExpiresAt
    ) {
        public ClaimedEvent {
            DomainPreconditions.requireNonNull(tenantId, "outboxTenantId");
            DomainPreconditions.requireNonNull(eventId, "outboxEventId");
            aggregateType = DomainPreconditions.requireText(aggregateType, "outboxAggregateType");
            DomainPreconditions.requireNonNull(aggregateId, "outboxAggregateId");
            DomainPreconditions.requireNonNull(sourceAggregateVersion, "outboxSourceAggregateVersion");
            eventType = DomainPreconditions.requireText(eventType, "outboxEventType");
            DomainPreconditions.require(schemaVersion > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "outbox schema version must be positive");
            DomainPreconditions.requireNonNull(correlationId, "outboxCorrelationId");
            payloadReferences = Map.copyOf(payloadReferences == null ? Map.of() : payloadReferences);
            DomainPreconditions.require(attemptNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "outbox attempt number must be positive");
            DomainPreconditions.requireNonNull(deliveryVersion, "outboxDeliveryVersion");
            DomainPreconditions.requireNonNull(claimExpiresAt, "outboxClaimExpiresAt");
        }
    }
}
