package com.ruoyi.interview.application.billing;

import com.ruoyi.interview.domain.billing.EntitlementState;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.UsageQuantity;

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
