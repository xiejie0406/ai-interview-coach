package com.ruoyi.interview.domain.billing;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;

/** append-only 用户权益结算事实；Provider 成本必须进入独立 Cost Ledger。 */
public record UsageSettlement(
        ResourceId id,
        TenantId tenantId,
        UserId userId,
        ResourceId reservationId,
        ResourceId businessOperationId,
        UsageQuantity reservedQuantity,
        UsageQuantity settledQuantity,
        UsageQuantity releasedQuantity,
        String ruleVersion,
        Instant settledAt
) {

    public UsageSettlement {
        DomainPreconditions.requireNonNull(id, "settlementId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(userId, "userId");
        DomainPreconditions.requireNonNull(reservationId, "reservationId");
        DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
        DomainPreconditions.requireNonNull(reservedQuantity, "reservedQuantity");
        DomainPreconditions.requireNonNull(settledQuantity, "settledQuantity");
        DomainPreconditions.requireNonNull(releasedQuantity, "releasedQuantity");
        DomainPreconditions.require(reservedQuantity.unit().equals(settledQuantity.unit())
                        && reservedQuantity.unit().equals(releasedQuantity.unit()),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "settlement usage units must match");
        DomainPreconditions.require(settledQuantity.plus(releasedQuantity).value()
                        .compareTo(reservedQuantity.value()) == 0,
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "settled and released quantities must equal reservation");
        ruleVersion = DomainPreconditions.requireText(ruleVersion, "settlementRuleVersion");
        DomainPreconditions.requireNonNull(settledAt, "settledAt");
    }
}
