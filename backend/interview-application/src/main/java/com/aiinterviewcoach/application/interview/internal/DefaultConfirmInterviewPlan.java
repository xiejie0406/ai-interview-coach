package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.billing.port.EntitlementPort;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.ConfirmInterviewPlan;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** 只接受同租户、同 owner、业务操作指向该 Plan 的 RESERVED reservation。 */
public final class DefaultConfirmInterviewPlan implements ConfirmInterviewPlan {

    private final InterviewRepository repository;
    private final EntitlementPort billing;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultConfirmInterviewPlan(InterviewRepository repository, EntitlementPort billing,
                                       ActivePrincipalGuard principal, IdempotencyGuard idempotency,
                                       DomainEventPort domainEvents, TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.billing = java.util.Objects.requireNonNull(billing);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public com.aiinterviewcoach.application.interview.InterviewPlanView handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("interview.plan.confirm", command.planId().value(),
                    command.acknowledgedEstimateVersion(),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.plan.confirm", requestHash, command.context()));
            var plan = repository.findPlan(owner.tenantId(), command.planId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview plan was not found", false, Map.of()));
            if (!plan.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview plan was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "interview plan confirmation is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return InterviewViews.plan(plan);
            }
            if (!plan.usageEstimate().ruleVersion().equals(command.acknowledgedEstimateVersion())) {
                throw new com.aiinterviewcoach.domain.platform.DomainException(
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.VERSION_CONFLICT,
                        "acknowledged usage estimate version is stale");
            }
            var reservation = billing.reserve(new EntitlementPort.ReserveRequest(
                    owner.tenantId(), owner.userId(), plan.id(), plan.usageEstimate().quantity(),
                    plan.expiresAt(), command.context().idempotencyKey(),
                    command.context().correlationId()));
            plan.confirm(reservation.reservationId(), command.expectedVersion(), command.context().eventContext());
            repository.savePlan(plan);
            domainEvents.append(plan.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.plan.confirm", requestHash,
                    Map.of("planId", plan.id().value(), "planVersionNo", Integer.toString(plan.planVersionNo())),
                    200, command.context()));
            return InterviewViews.plan(plan);
        });
    }
}
