package com.aiinterviewcoach.application.billing.port;

import com.aiinterviewcoach.domain.billing.Entitlement;
import com.aiinterviewcoach.domain.billing.UsageReservation;
import com.aiinterviewcoach.domain.billing.UsageSettlement;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

/** 预留时实现必须锁定/原子 CAS Entitlement，防止并发透支。 */
public interface BillingRepository {

    Optional<Entitlement> findEntitlement(TenantId tenantId, ResourceId entitlementId);

    List<Entitlement> findUsableEntitlements(TenantId tenantId, UserId userId, String unit);

    Optional<UsageReservation> findReservation(TenantId tenantId, ResourceId reservationId);

    Optional<UsageReservation> findReservationByOperation(
            TenantId tenantId,
            ResourceId businessOperationId,
            String unit
    );

    void saveEntitlement(Entitlement entitlement);

    void saveReservation(UsageReservation reservation);

    void appendSettlement(UsageSettlement settlement);
}
