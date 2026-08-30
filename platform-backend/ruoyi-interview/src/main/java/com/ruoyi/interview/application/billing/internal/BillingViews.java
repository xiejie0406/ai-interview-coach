package com.ruoyi.interview.application.billing.internal;

import com.ruoyi.interview.application.billing.EntitlementView;
import com.ruoyi.interview.application.billing.UsageReservationView;
import com.ruoyi.interview.domain.billing.Entitlement;
import com.ruoyi.interview.domain.billing.UsageReservation;

final class BillingViews {

    private BillingViews() {
    }

    static EntitlementView entitlement(Entitlement value) {
        return new EntitlementView(value.id(), value.state(), value.limit(), value.consumed(), value.reserved(),
                value.available(), value.validTo(), value.version());
    }

    static UsageReservationView reservation(UsageReservation value) {
        return new UsageReservationView(value.id(), value.entitlementId(), value.businessOperationId(),
                value.reservedQuantity(), value.state(), value.expiresAt(), value.settledQuantity(), value.version());
    }
}
