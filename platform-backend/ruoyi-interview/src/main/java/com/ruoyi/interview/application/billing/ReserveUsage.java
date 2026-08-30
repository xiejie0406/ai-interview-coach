package com.ruoyi.interview.application.billing;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.UsageQuantity;

import java.time.Instant;

/** Entitlement CAS 与 Reservation 创建必须位于同一本地事务。 */
@FunctionalInterface
public interface ReserveUsage {

    UsageReservationView handle(Command command);

    record Command(
            ResourceId businessOperationId,
            UsageQuantity requested,
            Instant expiresAt,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            DomainPreconditions.requireNonNull(requested, "requestedUsage");
            DomainPreconditions.requireNonNull(expiresAt, "expiresAt");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }
}
