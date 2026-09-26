package com.ruoyi.interview.application.billing.internal;

import com.ruoyi.interview.application.billing.ReleaseUsage;
import com.ruoyi.interview.application.billing.UsageReservationView;
import com.ruoyi.interview.application.billing.port.BillingRepository;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

/** 释放仅作用于当前租户 reservation，且必须同时归还 Entitlement reserved 数量。 */
public final class DefaultReleaseUsage implements ReleaseUsage {

    private final BillingRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultReleaseUsage(BillingRepository repository, ActivePrincipalGuard principal,
                               IdempotencyGuard idempotency, DomainEventPort domainEvents,
                               TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public UsageReservationView handle(Command command) {
        return transaction.required(() -> {
            command.context().requireAuthenticatedActor();
            var principalRef = command.context().principal().orElse(null);
            if (principalRef != null) {
                principal.requireActive(principalRef);
            }
            var reservation = repository.findReservation(command.context().requireTenantScope(), command.reservationId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "usage reservation was not found", false, Map.of()));
            if (principalRef != null && !reservation.userId().equals(principalRef.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "usage reservation was not found", false, Map.of());
            }
            String requestHash = ServerSideDigest.sha256("billing.release", command.reservationId().value(),
                    command.reasonCode());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "billing.release", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "usage release is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return BillingViews.reservation(reservation);
            }
            var entitlement = repository.findEntitlement(reservation.tenantId(), reservation.entitlementId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "reservation entitlement is missing", false, Map.of()));
            entitlement.release(reservation.reservedQuantity(), command.expectedEntitlementVersion(),
                    command.context().eventContext());
            reservation.release(command.reasonCode(), command.expectedReservationVersion(),
                    command.context().eventContext());
            repository.saveEntitlement(entitlement);
            repository.saveReservation(reservation);
            domainEvents.append(entitlement.pullDomainEvents());
            domainEvents.append(reservation.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("billing.release", requestHash,
                    Map.of("reservationId", reservation.id().value()), 200, command.context()));
            return BillingViews.reservation(reservation);
        });
    }
}
