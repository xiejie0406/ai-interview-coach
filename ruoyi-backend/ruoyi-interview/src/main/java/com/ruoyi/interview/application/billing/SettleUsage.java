package com.ruoyi.interview.application.billing;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.UsageQuantity;

/** Reservation、Entitlement 和 append-only settlement/usage fact 需在同一本地事务落定。 */
@FunctionalInterface
public interface SettleUsage {

    Result handle(Command command);

    record Command(
            ResourceId reservationId,
            UsageQuantity actualUsage,
            AggregateVersion expectedReservationVersion,
            AggregateVersion expectedEntitlementVersion,
            String settlementRuleVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(reservationId, "reservationId");
            DomainPreconditions.requireNonNull(actualUsage, "actualUsage");
            DomainPreconditions.requireNonNull(expectedReservationVersion, "expectedReservationVersion");
            DomainPreconditions.requireNonNull(expectedEntitlementVersion, "expectedEntitlementVersion");
            settlementRuleVersion = DomainPreconditions.requireText(
                    settlementRuleVersion, "settlementRuleVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requireAuthenticatedActor();
        }
    }

    record Result(UsageReservationView reservation, ResourceId settlementId) {
        public Result {
            DomainPreconditions.requireNonNull(reservation, "usageReservation");
            DomainPreconditions.requireNonNull(settlementId, "settlementId");
        }
    }
}
