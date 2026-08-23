package com.ruoyi.interview.application.learning.internal;

import com.ruoyi.interview.application.agent.learning.LearningCoachPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.learning.CreateLearningPlan;
import com.ruoyi.interview.application.learning.LearningPlanRepository;
import com.ruoyi.interview.application.learning.LearningPolicyPort;
import com.ruoyi.interview.application.learning.LearningSourceAccessPort;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.learning.LearningItemStatus;
import com.ruoyi.interview.domain.learning.LearningPlan;
import com.ruoyi.interview.domain.learning.LearningTask;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 owner-scoped ReportVersion 生成受控候选计划。幂等 claim 与最终持久化各使用短事务，
 * Learning Coach Provider 调用始终位于事务外。
 */
public final class DefaultCreateLearningPlan implements CreateLearningPlan {

    private static final String OPERATION = "learning.plan.create";

    private final LearningPlanRepository repository;
    private final LearningSourceAccessPort sourceAccess;
    private final LearningPolicyPort policy;
    private final LearningCoachPort coach;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultCreateLearningPlan(
            LearningPlanRepository repository,
            LearningSourceAccessPort sourceAccess,
            LearningPolicyPort policy,
            LearningCoachPort coach,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.sourceAccess = java.util.Objects.requireNonNull(sourceAccess);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.coach = java.util.Objects.requireNonNull(coach);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        var owner = principal.requireActive(command.context());
        IdempotencyGuard.Decision decision = transaction.required(() -> idempotency.begin(
                new IdempotencyGuard.BeginCommand(OPERATION, command.requestHash(), command.context())));
        if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
            return transaction.required(() -> replay(command, owner, decision.resourceReferences()));
        }
        requireNew(decision);

        try {
            LearningSourceAccessPort.Source source = loadSource(command, owner);
            var report = source.report();
            LearningPolicyPort.PolicySnapshot policySnapshot = policy.resolve(
                    owner.tenantId(), report.reportVersionId());
            LearningCoachPort.Result recommendation = coach.recommend(new LearningCoachPort.Request(
                    owner.tenantId(), report, source.allowedQuestions(), policySnapshot.configVersionId(),
                    policySnapshot.coachPin(),
                    policySnapshot.providerPolicy(), command.context().correlationId()));
            LearningCoachPort.Candidate candidate = requireValidatedCandidate(recommendation, report.reportVersionId());

            return transaction.required(() -> {
                var currentOwner = principal.requireActive(command.context());
                requireSameOwner(owner, currentOwner);
                LearningSourceAccessPort.Source current = loadSource(command, currentOwner);
                requireUnchangedSource(source, current);
                List<LearningTask> items = authoritativeItems(candidate, current.allowedQuestions());
                LearningPlan plan = LearningPlan.candidate(idGenerator.nextResourceId(), currentOwner.tenantId(),
                        currentOwner.userId(), command.sourceReportId(), report.reportVersionId(),
                        policySnapshot.configVersionId(), policySnapshot.coachPin(),
                        policySnapshot.providerPolicy(), items,
                        candidate.limitations(), command.context().eventContext());
                repository.save(plan);
                domainEvents.append(plan.pullDomainEvents());
                idempotency.succeed(new IdempotencyGuard.CompleteCommand(OPERATION, command.requestHash(),
                        Map.of("learningPlanId", plan.id().value(),
                                "learningPlanVersion", Long.toString(plan.version().value())),
                        201, command.context()));
                return new Result(plan);
            });
        } catch (RuntimeException failure) {
            recordFailure(command, failure);
            throw failure;
        }
    }

    private LearningSourceAccessPort.Source loadSource(
            Command command, com.ruoyi.interview.domain.platform.PrincipalRef owner) {
        LearningSourceAccessPort.Source source = sourceAccess.load(
                owner.tenantId(), owner.userId(), command.sourceReportId());
        if (!source.allowed()) {
            throw notFound();
        }
        if (!source.report().reportId().equals(command.sourceReportId())) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "learning source returned another report", false,
                    Map.of("reasonCode", "LEARNING_SOURCE_REPORT_MISMATCH"));
        }
        return source;
    }

    private static LearningCoachPort.Candidate requireValidatedCandidate(
            LearningCoachPort.Result result, ResourceId reportVersionId) {
        if (!result.schemaValidated() || result.candidate() == null) {
            throw new ApplicationException(ApplicationErrorCode.PROVIDER_BAD_RESPONSE,
                    "learning coach output did not match the pinned schema", false,
                    Map.of("reasonCode", "LEARNING_PLAN_SCHEMA_INVALID"));
        }
        LearningCoachPort.Candidate candidate = result.candidate();
        requireSameReportVersion(candidate.sourceReportVersionId(), reportVersionId);
        return candidate;
    }

    private List<LearningTask> authoritativeItems(
            LearningCoachPort.Candidate candidate,
            List<LearningCoachPort.AllowedQuestionVersion> allowedQuestions) {
        Map<ResourceId, LearningCoachPort.AllowedQuestionVersion> allowed = questionMap(allowedQuestions);
        return candidate.items().stream().map(item -> {
            LearningCoachPort.AllowedQuestionVersion question = allowed.get(item.questionVersionId());
            if (question == null) {
                throw new ApplicationException(ApplicationErrorCode.PROVIDER_BAD_RESPONSE,
                        "learning coach selected a question outside the allowlist", false,
                        Map.of("reasonCode", "LEARNING_QUESTION_NOT_ALLOWLISTED"));
            }
            return new LearningTask(idGenerator.nextResourceId(), question.questionVersionId(), question.title(),
                    item.weaknessRef(), item.reasonCodes(), item.priority(), item.suggestedDueAt(),
                    LearningItemStatus.PENDING, AggregateVersion.initial());
        }).toList();
    }

    private Result replay(Command command, com.ruoyi.interview.domain.platform.PrincipalRef owner,
                          Map<String, String> references) {
        ResourceId planId = ResourceId.of(required(references, "learningPlanId"));
        LearningPlan plan = repository.find(owner.tenantId(), planId).orElseThrow(DefaultCreateLearningPlan::notFound);
        if (!plan.userId().equals(owner.userId()) || !plan.sourceReportId().equals(command.sourceReportId())) {
            throw notFound();
        }
        long recordedVersion = parseVersion(required(references, "learningPlanVersion"));
        if (plan.version().value() != recordedVersion) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "the original idempotent response snapshot is no longer reconstructable", false,
                    Map.of("reasonCode", "IDEMPOTENT_RESPONSE_SNAPSHOT_UNAVAILABLE"));
        }
        return new Result(plan);
    }

    private void recordFailure(Command command, RuntimeException failure) {
        String errorCode = failure instanceof ApplicationException application
                ? application.code().name() : "LEARNING_PLAN_CREATION_FAILED";
        int responseStatus = failureStatus(failure);
        try {
            transaction.required(() -> idempotency.failReplayable(new IdempotencyGuard.FailCommand(
                    OPERATION, command.requestHash(), errorCode, responseStatus, Map.of(), command.context())));
        } catch (RuntimeException finalizationFailure) {
            failure.addSuppressed(finalizationFailure);
        }
    }

    private static int failureStatus(RuntimeException failure) {
        if (failure instanceof ApplicationException application) {
            return switch (application.code()) {
                case NOT_FOUND -> 404;
                case AUTH_REQUIRED -> 401;
                case FORBIDDEN -> 403;
                case PROVIDER_BAD_RESPONSE -> 502;
                case CAPABILITY_UNAVAILABLE -> 503;
                case DEADLINE_EXCEEDED -> 504;
                default -> 409;
            };
        }
        return 500;
    }

    private static void requireNew(IdempotencyGuard.Decision decision) {
        if (decision.type() != IdempotencyGuard.DecisionType.NEW) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "idempotency guard returned a non-executable decision", false,
                    Map.of("reasonCode", "IDEMPOTENCY_DECISION_UNEXPECTED"));
        }
    }

    private static void requireSameOwner(
            com.ruoyi.interview.domain.platform.PrincipalRef expected,
            com.ruoyi.interview.domain.platform.PrincipalRef actual) {
        if (!expected.equals(actual)) {
            throw new ApplicationException(ApplicationErrorCode.FORBIDDEN,
                    "authenticated principal changed while creating a learning plan", false, Map.of());
        }
    }

    private static void requireSameReportVersion(
            LearningCoachPort.ReportSnapshot report, ResourceId expectedVersionId) {
        requireSameReportVersion(report.reportVersionId(), expectedVersionId);
    }

    private static void requireUnchangedSource(
            LearningSourceAccessPort.Source expected, LearningSourceAccessPort.Source current) {
        Map<ResourceId, LearningCoachPort.AllowedQuestionVersion> expectedQuestions = questionMap(
                expected.allowedQuestions());
        Map<ResourceId, LearningCoachPort.AllowedQuestionVersion> currentQuestions = questionMap(
                current.allowedQuestions());
        if (!expected.report().equals(current.report()) || !expectedQuestions.equals(currentQuestions)) {
            throw new ApplicationException(ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                    "learning source changed while the candidate was being generated", true,
                    Map.of("reasonCode", "LEARNING_SOURCE_CHANGED"));
        }
    }

    private static Map<ResourceId, LearningCoachPort.AllowedQuestionVersion> questionMap(
            List<LearningCoachPort.AllowedQuestionVersion> questions) {
        Map<ResourceId, LearningCoachPort.AllowedQuestionVersion> result = new LinkedHashMap<>();
        for (LearningCoachPort.AllowedQuestionVersion question : questions) {
            if (result.put(question.questionVersionId(), question) != null) {
                throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "learning source contains duplicate question versions", false,
                        Map.of("reasonCode", "LEARNING_SOURCE_DUPLICATE_QUESTION"));
            }
        }
        return Map.copyOf(result);
    }

    private static void requireSameReportVersion(ResourceId actual, ResourceId expected) {
        if (!actual.equals(expected)) {
            throw new ApplicationException(ApplicationErrorCode.PROVIDER_BAD_RESPONSE,
                    "learning candidate does not target the pinned report version", false,
                    Map.of("reasonCode", "LEARNING_SOURCE_VERSION_MISMATCH"));
        }
    }

    private static String required(Map<String, String> references, String key) {
        String value = references.get(key);
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "learning idempotency replay is missing a reference", false,
                    Map.of("referenceKey", key));
        }
        return value;
    }

    private static long parseVersion(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "learning idempotency replay contains an invalid version", false,
                    Map.of("reasonCode", "IDEMPOTENCY_REFERENCE_INVALID"));
        }
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "learning source or plan was not found", false, Map.of());
    }
}
