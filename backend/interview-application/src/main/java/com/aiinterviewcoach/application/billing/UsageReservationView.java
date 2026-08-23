package com.aiinterviewcoach.application.billing;

import com.aiinterviewcoach.domain.billing.UsageReservationState;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.UsageQuantity;

import java.time.Instant;
import java.util.Optional;

public record UsageReservationView(
        ResourceId reservationId,
        ResourceId entitlementId,
        ResourceId businessOperationId,
        UsageQuantity reservedQuantity,
        UsageReservationState state,
        Instant expiresAt,
        Optional<UsageQuantity> settledQuantity,
        AggregateVersion version
) {
    public UsageReservationView {
        DomainPreconditions.requireNonNull(reservationId, "reservationId");
        DomainPreconditions.requireNonNull(entitlementId, "entitlementId");
        DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
        DomainPreconditions.requireNonNull(reservedQuantity, "reservedQuantity");
        DomainPreconditions.requireNonNull(state, "reservationState");
        DomainPreconditions.requireNonNull(expiresAt, "expiresAt");
        settledQuantity = settledQuantity == null ? Optional.empty() : settledQuantity;
        DomainPreconditions.requireNonNull(version, "reservationVersion");
    }
}
