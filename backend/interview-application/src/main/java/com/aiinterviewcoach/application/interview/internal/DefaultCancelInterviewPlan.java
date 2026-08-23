package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.billing.port.EntitlementPort;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.CancelInterviewPlan;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** 取消确认计划时，同一事务释放未结算权益预留。 */
public final class DefaultCancelInterviewPlan implements CancelInterviewPlan {

    private final InterviewRepository repository;
    private final EntitlementPort billing;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultCancelInterviewPlan(InterviewRepository repository, EntitlementPort billing,
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
            String requestHash = ServerSideDigest.sha256("interview.plan.cancel", command.planId().value(),
                    command.acknowledgedEstimateVersion(),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.plan.cancel", requestHash, command.context()));
            var plan = repository.findPlan(owner.tenantId(), command.planId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview plan was not found", false, Map.of()));
            if (!plan.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND, "interview plan was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "interview plan cancellation is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return InterviewViews.plan(plan);
            }
            if (!plan.usageEstimate().ruleVersion().equals(command.acknowledgedEstimateVersion())) {
                throw new com.aiinterviewcoach.domain.platform.DomainException(
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.VERSION_CONFLICT,
                        "acknowledged usage estimate version is stale");
            }
            if (plan.usageReservationId().isPresent()) {
                billing.release(new EntitlementPort.ReleaseRequest(
                        owner.tenantId(), owner.userId(), plan.id(), plan.usageReservationId().orElseThrow(),
                        "INTERVIEW_PLAN_CANCELLED", command.context().idempotencyKey(),
                        command.context().correlationId()));
            }
            plan.cancel(command.expectedVersion(), command.context().eventContext());
            repository.savePlan(plan);
            domainEvents.append(plan.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.plan.cancel", requestHash,
                    Map.of("planId", plan.id().value()), 200, command.context()));
            return InterviewViews.plan(plan);
        });
    }
}
