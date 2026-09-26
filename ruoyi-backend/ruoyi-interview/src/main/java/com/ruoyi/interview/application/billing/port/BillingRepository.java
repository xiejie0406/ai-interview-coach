package com.ruoyi.interview.application.billing.port;

import com.ruoyi.interview.domain.billing.Entitlement;
import com.ruoyi.interview.domain.billing.UsageReservation;
import com.ruoyi.interview.domain.billing.UsageSettlement;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

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
