package com.aiinterviewcoach.application.billing;

import com.aiinterviewcoach.domain.billing.EntitlementState;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.UsageQuantity;

import java.time.Instant;

public record EntitlementView(
        ResourceId entitlementId,
        EntitlementState state,
        UsageQuantity limit,
        UsageQuantity consumed,
        UsageQuantity reserved,
        UsageQuantity available,
        Instant validTo,
        AggregateVersion version
) {
    public EntitlementView {
        DomainPreconditions.requireNonNull(entitlementId, "entitlementId");
        DomainPreconditions.requireNonNull(state, "entitlementState");
        DomainPreconditions.requireNonNull(limit, "limit");
        DomainPreconditions.requireNonNull(consumed, "consumed");
        DomainPreconditions.requireNonNull(reserved, "reserved");
        DomainPreconditions.requireNonNull(available, "available");
        DomainPreconditions.requireNonNull(validTo, "validTo");
        DomainPreconditions.requireNonNull(version, "entitlementVersion");
    }
}
