package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.CreateInterviewPlan;
import com.ruoyi.interview.application.interview.InterviewPlanView;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.interview.port.InterviewPlanPolicyPort;
import com.ruoyi.interview.application.interview.port.PlanQuestionSelectionPort;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.interview.InterviewPlan;

import java.util.Map;

/** 题目选择和用量预估由确定性端口提供，Plan 聚合只负责版本与确认门。 */
public final class DefaultCreateInterviewPlan implements CreateInterviewPlan {

    private final InterviewRepository repository;
    private final PlanQuestionSelectionPort selection;
    private final InterviewPlanPolicyPort policy;
    private final ProfileAccessPort profileAccess;
    private final PlanUsageEstimator usageEstimator;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultCreateInterviewPlan(InterviewRepository repository, PlanQuestionSelectionPort selection,
                                      InterviewPlanPolicyPort policy,
                                      ProfileAccessPort profileAccess, PlanUsageEstimator usageEstimator, ActivePrincipalGuard principal,
                                      IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                                      DomainEventPort domainEvents, TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.selection = java.util.Objects.requireNonNull(selection);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.profileAccess = java.util.Objects.requireNonNull(profileAccess);
        this.usageEstimator = java.util.Objects.requireNonNull(usageEstimator);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public InterviewPlanView handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("interview.plan.create",
                    command.targetRole().name(), command.targetLevel().name(),
                    command.topics().stream().sorted().collect(java.util.stream.Collectors.joining(",")),
                    Integer.toString(command.durationMinutes()), command.mode().name());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "interview.plan.create", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "interview plan creation is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String planId = decision.resourceReferences().get("planId");
                if (planId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "plan replay has no plan reference", false, Map.of());
                }
                return repository.findPlan(owner.tenantId(), com.ruoyi.interview.domain.platform.ResourceId.of(planId))
                        .map(InterviewViews::plan)
                        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "plan replay record is missing", false, Map.of()));
            }
            var resolved = policy.resolve(new InterviewPlanPolicyPort.Request(
                    owner.tenantId(), owner.userId(), command.targetRole(), command.targetLevel(),
                    command.topics(), command.durationMinutes(), command.mode(),
                    command.context().requestedAt()));
            if (!resolved.expiresAt().isAfter(command.context().requestedAt())) {
                throw new com.ruoyi.interview.domain.platform.DomainException(
                        com.ruoyi.interview.domain.platform.DomainErrorCode.PLAN_EXPIRED,
                        "interview plan policy returned an expired deadline");
            }
            if (!profileAccess.belongsTo(owner.tenantId(), owner.userId(), resolved.profileVersion())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "profile version was not found", false, Map.of());
            }
            var questions = selection.select(new PlanQuestionSelectionPort.SelectionRequest(owner.tenantId(),
                    resolved.topicCodes(), resolved.targetRoles(), resolved.difficulties(), resolved.timeBudget(),
                    resolved.questionCount(), resolved.followUpBudget()));
            var estimate = usageEstimator.estimate(questions, resolved.followUpBudget());
            var plan = InterviewPlan.draft(idGenerator.nextResourceId(), owner.tenantId(), owner.userId(),
                    resolved.profileVersion(), command.mode(), resolved.expiresAt(), questions,
                    resolved.timeBudget(), resolved.followUpBudget(), estimate,
                    ServerSideDigest.sha256(resolved.policyVersion(), resolved.profileVersion().toString(),
                            questions.toString(), estimate.toString()),
                    command.context().eventContext());
            repository.savePlan(plan);
            domainEvents.append(plan.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("interview.plan.create", requestHash,
                    Map.of("planId", plan.id().value()), 201, command.context()));
            return InterviewViews.plan(plan);
        });
    }
}
