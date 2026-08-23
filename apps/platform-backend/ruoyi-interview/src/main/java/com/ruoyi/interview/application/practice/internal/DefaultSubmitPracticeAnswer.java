package com.ruoyi.interview.application.practice.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.practice.SubmitPracticeAnswer;
import com.ruoyi.interview.application.practice.port.EvaluationRequestPort;
import com.ruoyi.interview.application.practice.port.PracticeRepository;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.application.shared.OperationAccepted;
import com.ruoyi.interview.domain.practice.AnswerVersion;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** 提交回答、排队 Evaluation 和幂等结果在同一本地事务中受理。 */
public final class DefaultSubmitPracticeAnswer implements SubmitPracticeAnswer {

    private final PracticeRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final EvaluationRequestPort evaluationRequests;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultSubmitPracticeAnswer(PracticeRepository repository, ActivePrincipalGuard principal,
                                       IdGeneratorPort idGenerator, EvaluationRequestPort evaluationRequests,
                                       IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                       TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.evaluationRequests = java.util.Objects.requireNonNull(evaluationRequests);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("practice.answer", command.attemptId().value(),
                    command.source().name(), command.answerText());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "practice.answer", requestHash, command.context()));
            var attempt = repository.find(owner.tenantId(), command.attemptId()).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "practice attempt was not found", false, Map.of()));
            if (!attempt.userId().equals(owner.userId())) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "practice attempt was not found", false, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "practice answer is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String answerId = decision.resourceReferences().get("answerVersionId");
                if (answerId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "practice answer replay has no answer reference", false, Map.of());
                }
                return new Result(PracticeViews.view(attempt), com.ruoyi.interview.domain.platform.ResourceId.of(answerId),
                        replayAccepted(decision.resourceReferences()));
            }
            int versionNo = attempt.answerVersions().size() + 1;
            AnswerVersion answer = new AnswerVersion(idGenerator.nextResourceId(), owner.tenantId(), attempt.id(),
                    versionNo, command.source(), command.answerText(), ServerSideDigest.sha256(command.answerText()),
                    owner.userId(), command.context().requestedAt(), Optional.empty());
            attempt.submit(answer, command.expectedVersion(), command.context().eventContext());
            OperationAccepted accepted = evaluationRequests.request(new EvaluationRequestPort.Request(
                    owner.tenantId(), answer.id(), attempt.questionVersion(), attempt.rubricVersion(), answer.id(),
                    command.context().correlationId()));
            repository.save(attempt);
            domainEvents.append(attempt.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("practice.answer", requestHash,
                    acceptedReferences(answer.id(), accepted), 202, command.context()));
            return new Result(PracticeViews.view(attempt), answer.id(), Optional.of(accepted));
        });
    }

    private static Map<String, String> acceptedReferences(com.ruoyi.interview.domain.platform.ResourceId answerId,
                                                          OperationAccepted accepted) {
        Map<String, String> refs = new java.util.LinkedHashMap<>();
        refs.put("answerVersionId", answerId.value());
        refs.put("operationId", accepted.operationId().value());
        accepted.jobId().ifPresent(value -> refs.put("jobId", value.value()));
        accepted.resourceId().ifPresent(value -> refs.put("resourceId", value.value()));
        refs.put("statusPath", accepted.statusPath());
        accepted.streamPath().ifPresent(value -> refs.put("streamPath", value));
        refs.put("acceptedAt", accepted.acceptedAt().toString());
        return refs;
    }

    private static Optional<OperationAccepted> replayAccepted(Map<String, String> refs) {
        String operationId = refs.get("operationId");
        String statusPath = refs.get("statusPath");
        String acceptedAt = refs.get("acceptedAt");
        if (operationId == null || statusPath == null || acceptedAt == null) {
            return Optional.empty();
        }
        return Optional.of(new OperationAccepted(com.ruoyi.interview.domain.platform.ResourceId.of(operationId),
                Optional.ofNullable(refs.get("jobId")).map(com.ruoyi.interview.domain.platform.ResourceId::of),
                Optional.ofNullable(refs.get("resourceId")).map(com.ruoyi.interview.domain.platform.ResourceId::of),
                statusPath, Optional.ofNullable(refs.get("streamPath")), Instant.parse(acceptedAt)));
    }
}
