package com.aiinterviewcoach.application.billing;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

@FunctionalInterface
public interface ReleaseUsage {

    UsageReservationView handle(Command command);

    record Command(
            ResourceId reservationId,
            AggregateVersion expectedReservationVersion,
            AggregateVersion expectedEntitlementVersion,
            String reasonCode,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(reservationId, "reservationId");
            DomainPreconditions.requireNonNull(expectedReservationVersion, "expectedReservationVersion");
            DomainPreconditions.requireNonNull(expectedEntitlementVersion, "expectedEntitlementVersion");
            reasonCode = DomainPreconditions.requireText(reasonCode, "releaseReasonCode");
            DomainPreconditions.require(reasonCode.length() <= 96,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "releaseReasonCode is too long");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requireAuthenticatedActor();
        }

        @Override
        public String toString() {
            return "Command[reservationId=" + reservationId
                    + ", expectedReservationVersion=" + expectedReservationVersion
                    + ", expectedEntitlementVersion=" + expectedEntitlementVersion
                    + ", reasonCode=<redacted>, context=" + context + "]";
        }
    }
}
