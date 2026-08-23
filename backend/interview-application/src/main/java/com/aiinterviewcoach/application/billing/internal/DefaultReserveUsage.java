package com.aiinterviewcoach.application.billing.internal;

import com.aiinterviewcoach.application.billing.ReserveUsage;
import com.aiinterviewcoach.application.billing.UsageReservationView;
import com.aiinterviewcoach.application.billing.port.BillingRepository;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.billing.UsageReservation;
import com.aiinterviewcoach.domain.billing.EntitlementState;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;

import java.util.Map;

/** 以确定性顺序选择权益，并在同一事务内创建 Reservation 与扣减 reserved。 */
public final class DefaultReserveUsage implements ReserveUsage {

    private final BillingRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultReserveUsage(BillingRepository repository, ActivePrincipalGuard principal,
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
    public UsageReservationView handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("billing.reserve", command.businessOperationId().value(),
                    command.requested().unit(), command.requested().value().toPlainString(), command.expiresAt().toString());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "billing.reserve", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "usage reservation is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String reservationId = decision.resourceReferences().get("reservationId");
                if (reservationId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "reservation replay has no reservation reference", false, Map.of());
                }
                return repository.findReservation(owner.tenantId(), com.aiinterviewcoach.domain.platform.ResourceId.of(reservationId))
                        .map(BillingViews::reservation)
                        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                                "reservation replay record is missing", false, Map.of()));
            }
            var entitlement = repository.findUsableEntitlements(owner.tenantId(), owner.userId(), command.requested().unit())
                    .stream()
                    .filter(candidate -> candidate.state() == EntitlementState.ACTIVE
                            && !command.context().requestedAt().isBefore(candidate.validFrom())
                            && command.context().requestedAt().isBefore(candidate.validTo()))
                    .filter(candidate -> candidate.available().value().compareTo(command.requested().value()) >= 0)
                    .findFirst()
                    .orElseThrow(() -> new DomainException(DomainErrorCode.USAGE_EXCEEDED,
                            "no active entitlement has enough available usage"));
            entitlement.reserve(command.requested(), entitlement.version(), command.context().requestedAt(),
                    command.context().eventContext());
            UsageReservation reservation = UsageReservation.reserve(idGenerator.nextResourceId(), owner.tenantId(),
                    owner.userId(), entitlement.id(), command.businessOperationId(), command.context().idempotencyKey(),
                    command.requested(), command.expiresAt(), command.context().eventContext());
            repository.saveEntitlement(entitlement);
            repository.saveReservation(reservation);
            domainEvents.append(entitlement.pullDomainEvents());
            domainEvents.append(reservation.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("billing.reserve", requestHash,
                    Map.of("reservationId", reservation.id().value(), "entitlementId", entitlement.id().value()),
                    201, command.context()));
            return BillingViews.reservation(reservation);
        });
    }
}
