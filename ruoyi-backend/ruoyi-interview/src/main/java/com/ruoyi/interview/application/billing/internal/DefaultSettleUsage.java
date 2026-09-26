package com.ruoyi.interview.application.billing.internal;

import com.ruoyi.interview.application.billing.SettleUsage;
import com.ruoyi.interview.application.billing.UsageReservationView;
import com.ruoyi.interview.application.billing.port.BillingRepository;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.billing.UsageReservation;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.ServiceActorRef;

import java.util.Map;

/** 结算只接受 reservation 内已预留额度；实际用量过大由领域规则拒绝。 */
public final class DefaultSettleUsage implements SettleUsage {

    private final BillingRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultSettleUsage(BillingRepository repository, ActivePrincipalGuard principal,
                              IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                              DomainEventPort domainEvents, TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
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
            String requestHash = ServerSideDigest.sha256("billing.settle", command.reservationId().value(),
                    command.actualUsage().unit(), command.actualUsage().value().toPlainString(), command.settlementRuleVersion());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "billing.settle", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "usage settlement is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String settlementId = decision.resourceReferences().get("settlementId");
                if (settlementId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "settlement replay has no settlement reference", false, Map.of());
                }
                return new Result(BillingViews.reservation(reservation),
                        com.ruoyi.interview.domain.platform.ResourceId.of(settlementId));
            }
            var entitlement = repository.findEntitlement(reservation.tenantId(), reservation.entitlementId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "reservation entitlement is missing", false, Map.of()));
            if (!entitlement.userId().equals(reservation.userId())) {
                throw new DomainException(com.ruoyi.interview.domain.platform.DomainErrorCode.OWNERSHIP_DENIED,
                        "reservation entitlement owner mismatch");
            }
            var settlement = reservation.settle(idGenerator.nextResourceId(), command.settlementRuleVersion(),
                    command.actualUsage(), command.expectedReservationVersion(), command.context().requestedAt(),
                    command.context().eventContext());
            entitlement.settle(reservation.reservedQuantity(), command.actualUsage(),
                    command.expectedEntitlementVersion(), command.context().requestedAt(), command.context().eventContext());
            repository.saveEntitlement(entitlement);
            repository.saveReservation(reservation);
            repository.appendSettlement(settlement);
            domainEvents.append(entitlement.pullDomainEvents());
            domainEvents.append(reservation.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("billing.settle", requestHash,
                    Map.of("settlementId", settlement.id().value()), 200, command.context()));
            return new Result(BillingViews.reservation(reservation), settlement.id());
        });
    }
}
