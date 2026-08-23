package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.SubmitInterviewAnswer;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Map;
import java.util.Optional;

/**
 * 文本回答、稳定点与下一步 Job 同一事务落库；确认转写必须走 ConfirmTranscript 原子入口，
 * ASR partial/final 或单独的 transcript version 引用不会直接进入本用例。
 */
public final class DefaultSubmitInterviewAnswer implements SubmitInterviewAnswer {

    private static final String OPERATION = "interview.answer";

    private final InterviewRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdempotencyGuard idempotency;
    private final TransactionPort transaction;
    private final InterviewSnapshotFactory snapshots;
    private final InterviewAnswerCommitter committer;

    public DefaultSubmitInterviewAnswer(
            InterviewRepository repository,
            JobPort jobs,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction,
            InterviewRecoveryProjectionPort projections
    ) {
        this(repository, jobs, principal, idGenerator, idempotency, domainEvents,
                transaction, projections, 3);
    }

    public DefaultSubmitInterviewAnswer(
            InterviewRepository repository,
            JobPort jobs,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction,
            InterviewRecoveryProjectionPort projections,
            int maxAttempts
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.snapshots = new InterviewSnapshotFactory(projections);
        this.committer = new InterviewAnswerCommitter(
                repository, jobs, idGenerator, domainEvents, maxAttempts);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            if (command.source() != com.ruoyi.interview.domain.interview.InterviewAnswerSource.TEXT) {
                throw new com.ruoyi.interview.domain.platform.DomainException(
                        com.ruoyi.interview.domain.platform.DomainErrorCode.POLICY_DENIED,
                        "confirmed transcript answers require the transcript confirmation command");
            }
            String requestHash = ServerSideDigest.sha256(
                    OPERATION,
                    command.sessionId().value(),
                    command.turnId().value(),
                    Integer.toString(command.turnSequence()),
                    command.source().name(),
                    command.answerText(),
                    command.confirmedTranscriptVersionId().map(ResourceId::value).orElse(""),
                    Long.toString(command.expectedSessionVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(
                    new IdempotencyGuard.BeginCommand(OPERATION, requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                var session = repository.findSession(owner.tenantId(), command.sessionId())
                        .orElseThrow(DefaultSubmitInterviewAnswer::notFound);
                String answerId = requiredReference(decision.resourceReferences(), "answerVersionId");
                return new Result(snapshots.create(session, owner), ResourceId.of(answerId),
                        OperationAcceptedFactory.replay(decision.resourceReferences()));
            }
            rejectUnexpectedDecision(decision);

            var committed = committer.commit(
                    owner,
                    command.sessionId(),
                    command.turnId(),
                    Optional.of(command.turnSequence()),
                    command.source(),
                    command.answerText(),
                    command.confirmedTranscriptVersionId(),
                    Optional.of(command.expectedSessionVersion()),
                    command.context());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    OPERATION,
                    requestHash,
                    OperationAcceptedFactory.idempotencyReferences(
                            committed.nextStepOperation(),
                            Map.of("answerVersionId", committed.answerVersionId().value())),
                    202,
                    command.context()));
            return new Result(
                    snapshots.create(committed.session(), owner),
                    committed.answerVersionId(),
                    committed.nextStepOperation());
        });
    }

    private static void rejectUnexpectedDecision(IdempotencyGuard.Decision decision) {
        if (decision.type() == IdempotencyGuard.DecisionType.NEW) {
            return;
        }
        ApplicationErrorCode code = decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS
                ? ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS
                : ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE;
        throw new ApplicationException(code, "interview answer idempotency decision rejected", true, Map.of());
    }

    private static String requiredReference(Map<String, String> references, String name) {
        String value = references.get(name);
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "interview answer replay has incomplete references", false,
                    Map.of("referenceKey", name));
        }
        return value;
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "interview session was not found", false, Map.of());
    }
}
