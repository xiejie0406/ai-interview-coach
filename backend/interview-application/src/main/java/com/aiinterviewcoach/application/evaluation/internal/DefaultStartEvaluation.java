package com.aiinterviewcoach.application.evaluation.internal;

import com.aiinterviewcoach.application.evaluation.EvaluationPolicyPort;
import com.aiinterviewcoach.application.evaluation.EvaluationRepository;
import com.aiinterviewcoach.application.evaluation.EvaluationSourceAccessPort;
import com.aiinterviewcoach.application.evaluation.StartEvaluation;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.domain.evaluation.EvaluationRun;
import com.aiinterviewcoach.domain.platform.Job;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Evaluation、Job、Outbox 与幂等回执在同一本地事务 durable 受理。 */
public final class DefaultStartEvaluation implements StartEvaluation {

    private final EvaluationRepository repository;
    private final EvaluationSourceAccessPort sourceAccess;
    private final EvaluationPolicyPort policy;
    private final JobPort jobs;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;
    private final int maxAttempts;

    public DefaultStartEvaluation(EvaluationRepository repository, EvaluationSourceAccessPort sourceAccess,
                                  EvaluationPolicyPort policy, JobPort jobs, IdGeneratorPort idGenerator,
                                  IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                  TransactionPort transaction) {
        this(repository, sourceAccess, policy, jobs, idGenerator, idempotency, domainEvents, transaction, 3);
    }

    public DefaultStartEvaluation(EvaluationRepository repository, EvaluationSourceAccessPort sourceAccess,
                                  EvaluationPolicyPort policy, JobPort jobs, IdGeneratorPort idGenerator,
                                  IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                  TransactionPort transaction, int maxAttempts) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.sourceAccess = java.util.Objects.requireNonNull(sourceAccess);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var principal = command.context().requirePrincipal();
            var decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "evaluation.start", command.requestHash(), command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return new Result(replay(decision.resourceReferences()));
            }
            var inspected = sourceAccess.inspect(principal.tenantId(), principal.userId(), command.answerVersionId());
            if (!inspected.allowed()) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "evaluation source was not found", false, Map.of());
            }
            var sourceRef = inspected.sourceRef();
            var policySnapshot = policy.resolve(principal.tenantId(), sourceRef);
            ResourceId evaluationId = idGenerator.nextResourceId();
            ResourceId jobId = idGenerator.nextResourceId();
            EvaluationRun run = EvaluationRun.request(evaluationId, principal.tenantId(), principal.userId(),
                    sourceRef, policySnapshot, command.context().eventContext());
            Job job = Job.schedule(jobId, principal.tenantId(), "EVALUATION_PIPELINE", evaluationId,
                    Map.of("evaluationId", evaluationId.value()), maxAttempts, command.context().requestedAt(),
                    command.context().eventContext());
            OperationAccepted accepted = accepted(run.id(), job.id(), command.context().requestedAt());
            repository.saveRun(run);
            jobs.save(job);
            domainEvents.append(run.pullDomainEvents());
            domainEvents.append(job.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("evaluation.start", command.requestHash(),
                    references(accepted), 202, command.context()));
            return new Result(accepted);
        });
    }

    private static OperationAccepted accepted(ResourceId evaluationId, ResourceId jobId, Instant at) {
        return new OperationAccepted(evaluationId, Optional.of(jobId), Optional.of(evaluationId),
                "/api/v1/evaluations/" + evaluationId.value(),
                Optional.of("/api/v1/streams/evaluations/" + evaluationId.value()), at);
    }

    private static Map<String, String> references(OperationAccepted value) {
        LinkedHashMap<String, String> refs = new LinkedHashMap<>();
        refs.put("operationId", value.operationId().value());
        value.jobId().ifPresent(item -> refs.put("jobId", item.value()));
        value.resourceId().ifPresent(item -> refs.put("resourceId", item.value()));
        refs.put("statusPath", value.statusPath());
        value.streamPath().ifPresent(item -> refs.put("streamPath", item));
        refs.put("acceptedAt", value.acceptedAt().toString());
        return Map.copyOf(refs);
    }

    private static OperationAccepted replay(Map<String, String> refs) {
        try {
            return new OperationAccepted(ResourceId.of(required(refs, "operationId")),
                    Optional.ofNullable(refs.get("jobId")).map(ResourceId::of),
                    Optional.ofNullable(refs.get("resourceId")).map(ResourceId::of),
                    required(refs, "statusPath"), Optional.ofNullable(refs.get("streamPath")),
                    Instant.parse(required(refs, "acceptedAt")));
        } catch (RuntimeException ex) {
            if (ex instanceof ApplicationException) {
                throw ex;
            }
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "evaluation replay references are invalid", false, Map.of());
        }
    }

    private static String required(Map<String, String> refs, String key) {
        String value = refs.get(key);
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "evaluation replay is missing a reference", false, Map.of("referenceKey", key));
        }
        return value;
    }
}
