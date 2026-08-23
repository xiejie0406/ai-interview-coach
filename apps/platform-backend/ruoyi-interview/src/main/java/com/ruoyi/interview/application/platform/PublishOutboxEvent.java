package com.ruoyi.interview.application.platform;

import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;

/** Publisher adapter 只负责传输；claim/publish/fail 状态由此应用用例协调。 */
@FunctionalInterface
public interface PublishOutboxEvent {

    void handle(Command command);

    record Command(
            TenantId tenantId,
            ResourceId eventId,
            String publisherId,
            AggregateVersion expectedVersion,
            Instant publishedAt
    ) {
        public Command {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(eventId, "eventId");
            publisherId = DomainPreconditions.requireText(publisherId, "publisherId");
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(publishedAt, "publishedAt");
        }
    }
}
