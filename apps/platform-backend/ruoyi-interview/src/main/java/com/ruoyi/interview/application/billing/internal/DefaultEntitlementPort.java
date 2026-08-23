package com.ruoyi.interview.application.billing.internal;

import com.ruoyi.interview.application.billing.CheckEntitlement;
import com.ruoyi.interview.application.billing.ReserveUsage;
import com.ruoyi.interview.application.billing.ReleaseUsage;
import com.ruoyi.interview.application.billing.port.EntitlementPort;
import com.ruoyi.interview.application.billing.port.BillingRepository;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.PrincipalRef;

import java.util.Optional;

/** Interview/Practice 使用的 billing facade；仍委托同一套事务与幂等用例。 */
public final class DefaultEntitlementPort implements EntitlementPort {

    private final CheckEntitlement check;
    private final ReserveUsage reserve;
    private final ReleaseUsage release;
    private final BillingRepository repository;
    private final ActivePrincipalGuard principal;
    private final ClockPort clock;

    public DefaultEntitlementPort(CheckEntitlement check, ReserveUsage reserve, ReleaseUsage release,
                                  BillingRepository repository, ActivePrincipalGuard principal, ClockPort clock) {
        this.check = java.util.Objects.requireNonNull(check);
        this.reserve = java.util.Objects.requireNonNull(reserve);
        this.release = java.util.Objects.requireNonNull(release);
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public com.ruoyi.interview.application.billing.EntitlementView check(CheckRequest request) {
        return check.handle(new CheckEntitlement.Query(request.required(),
                new QueryContext(new PrincipalRef(request.tenantId(), request.userId()),
                        request.correlationId(), request.at())));
    }

    @Override
    public com.ruoyi.interview.application.billing.UsageReservationView reserve(ReserveRequest request) {
        var context = new OperationContext(Optional.of(new PrincipalRef(request.tenantId(), request.userId())),
                Optional.empty(),
                com.ruoyi.interview.application.platform.ServerSideDigest.sha256(
                        request.tenantId().value(), request.userId().value()), request.correlationId(),
                request.idempotencyKey(), clock.now());
        return reserve.handle(new ReserveUsage.Command(request.businessOperationId(), request.required(),
                request.expiresAt(), context));
    }

    @Override
    public com.ruoyi.interview.application.billing.UsageReservationView requireActiveReservation(
            ActiveReservationRequest request) {
        principal.requireActive(new PrincipalRef(request.tenantId(), request.userId()));
        var reservation = ownedReservation(request.tenantId(), request.userId(),
                request.businessOperationId(), request.reservationId());
        if (reservation.state() != com.ruoyi.interview.domain.billing.UsageReservationState.RESERVED
                || !request.at().isBefore(reservation.expiresAt())) {
            throw new com.ruoyi.interview.domain.platform.DomainException(
                    com.ruoyi.interview.domain.platform.DomainErrorCode.POLICY_DENIED,
                    "usage reservation is not active");
        }
        return BillingViews.reservation(reservation);
    }

    @Override
    public void release(ReleaseRequest request) {
        principal.requireActive(new PrincipalRef(request.tenantId(), request.userId()));
        var reservation = ownedReservation(request.tenantId(), request.userId(),
                request.businessOperationId(), request.reservationId());
        if (reservation.state() == com.ruoyi.interview.domain.billing.UsageReservationState.RELEASED) {
            return;
        }
        if (reservation.state() != com.ruoyi.interview.domain.billing.UsageReservationState.RESERVED) {
            throw new com.ruoyi.interview.domain.platform.DomainException(
                    com.ruoyi.interview.domain.platform.DomainErrorCode.POLICY_DENIED,
                    "usage reservation cannot be released from its current state");
        }
        var context = new OperationContext(Optional.of(new PrincipalRef(request.tenantId(), request.userId())),
                Optional.empty(),
                com.ruoyi.interview.application.platform.ServerSideDigest.sha256(
                        request.tenantId().value(), request.userId().value()), request.correlationId(),
                request.idempotencyKey(), clock.now());
        var entitlement = repository.findEntitlement(request.tenantId(), reservation.entitlementId())
                .orElseThrow(() -> new com.ruoyi.interview.application.shared.ApplicationException(
                        com.ruoyi.interview.application.shared.ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "reservation entitlement is missing", false, java.util.Map.of()));
        release.handle(new ReleaseUsage.Command(request.reservationId(), reservation.version(),
                entitlement.version(), request.reasonCode(), context));
    }

    private com.ruoyi.interview.domain.billing.UsageReservation ownedReservation(
            com.ruoyi.interview.domain.platform.TenantId tenantId,
            com.ruoyi.interview.domain.platform.UserId userId,
            com.ruoyi.interview.domain.platform.ResourceId businessOperationId,
            com.ruoyi.interview.domain.platform.ResourceId reservationId) {
        var reservation = repository.findReservation(tenantId, reservationId)
                .orElseThrow(DefaultEntitlementPort::reservationNotFound);
        if (!reservation.userId().equals(userId)
                || !reservation.businessOperationId().equals(businessOperationId)) {
            throw reservationNotFound();
        }
        return reservation;
    }

    private static com.ruoyi.interview.application.shared.ApplicationException reservationNotFound() {
        return new com.ruoyi.interview.application.shared.ApplicationException(
                com.ruoyi.interview.application.shared.ApplicationErrorCode.NOT_FOUND,
                "usage reservation was not found", false, java.util.Map.of());
    }
}
