package com.ruoyi.interview.application.billing.port;

import com.ruoyi.interview.application.billing.EntitlementView;
import com.ruoyi.interview.application.billing.UsageReservationView;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.IdempotencyKey;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;

/** Interview/Practice 对 Billing 的公开能力；consumer 不访问 billing Repository。 */
public interface EntitlementPort {

    EntitlementView check(CheckRequest request);

    UsageReservationView reserve(ReserveRequest request);

    UsageReservationView requireActiveReservation(ActiveReservationRequest request);

    void release(ReleaseRequest request);

    record CheckRequest(
            TenantId tenantId,
            UserId userId,
            UsageQuantity required,
            CorrelationId correlationId,
            Instant at
    ) {
        public CheckRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(required, "requiredUsage");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
            DomainPreconditions.requireNonNull(at, "at");
        }
    }

    record ReserveRequest(
            TenantId tenantId,
            UserId userId,
            ResourceId businessOperationId,
            UsageQuantity required,
            Instant expiresAt,
            IdempotencyKey idempotencyKey,
            CorrelationId correlationId
    ) {
        public ReserveRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            DomainPreconditions.requireNonNull(required, "requiredUsage");
            DomainPreconditions.requireNonNull(expiresAt, "expiresAt");
            DomainPreconditions.requireNonNull(idempotencyKey, "idempotencyKey");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }

    record ReleaseRequest(
            TenantId tenantId,
            UserId userId,
            ResourceId businessOperationId,
            ResourceId reservationId,
            String reasonCode,
            IdempotencyKey idempotencyKey,
            CorrelationId correlationId
    ) {
        public ReleaseRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            DomainPreconditions.requireNonNull(reservationId, "reservationId");
            reasonCode = DomainPreconditions.requireText(reasonCode, "releaseReasonCode");
            DomainPreconditions.require(reasonCode.length() <= 96,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "releaseReasonCode is too long");
            DomainPreconditions.requireNonNull(idempotencyKey, "idempotencyKey");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }

        @Override
        public String toString() {
            return "ReleaseRequest[tenantId=" + tenantId + ", userId=" + userId
                    + ", businessOperationId=" + businessOperationId
                    + ", reservationId=" + reservationId + ", reasonCode=<redacted>"
                    + ", idempotencyKey=<redacted>, correlationId=" + correlationId + "]";
        }
    }

    record ActiveReservationRequest(
            TenantId tenantId,
            UserId userId,
            ResourceId businessOperationId,
            ResourceId reservationId,
            Instant at
    ) {
        public ActiveReservationRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            DomainPreconditions.requireNonNull(reservationId, "reservationId");
            DomainPreconditions.requireNonNull(at, "at");
        }
    }
}
