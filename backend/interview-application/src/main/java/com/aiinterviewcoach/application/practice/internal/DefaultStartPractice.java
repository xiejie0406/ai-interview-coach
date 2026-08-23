package com.aiinterviewcoach.application.practice.internal;

import com.aiinterviewcoach.application.catalog.port.PublishedQuestionPort;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.practice.PracticeAttemptView;
import com.aiinterviewcoach.application.practice.StartPractice;
import com.aiinterviewcoach.application.practice.port.PracticeRepository;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.practice.PracticeAttempt;

import java.util.Map;

/** 从发布题目快照创建 owner-scoped 文本练习。 */
public final class DefaultStartPractice implements StartPractice {

    private final PracticeRepository repository;
    private final PublishedQuestionPort publishedQuestions;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultStartPractice(PracticeRepository repository, PublishedQuestionPort publishedQuestions,
                                ActivePrincipalGuard principal, IdGeneratorPort idGenerator,
                                IdempotencyGuard idempotency, DomainEventPort domainEvents,
                                TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.publishedQuestions = java.util.Objects.requireNonNull(publishedQuestions);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public PracticeAttemptView handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("practice.start", command.questionVersion().toString(),
                    command.rubricVersion().toString());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "practice.start", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "practice start is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String attemptId = decision.resourceReferences().get("attemptId");
                if (attemptId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "practice replay has no attempt reference", false, Map.of());
                }
                return repository.find(owner.tenantId(), com.aiinterviewcoach.domain.platform.ResourceId.of(attemptId))
                        .map(PracticeViews::view)
                        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                                "practice replay attempt is missing", false, Map.of()));
            }
            if (publishedQuestions.findPublishedVersion(owner.tenantId(), command.questionVersion(),
                    command.rubricVersion()).isEmpty()) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "published question version was not found", false, Map.of());
            }
            PracticeAttempt attempt = PracticeAttempt.start(idGenerator.nextResourceId(), owner.tenantId(),
                    owner.userId(), command.questionVersion(), command.rubricVersion(), command.context().eventContext());
            repository.save(attempt);
            domainEvents.append(attempt.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand("practice.start", requestHash,
                    Map.of("attemptId", attempt.id().value()), 201, command.context()));
            return PracticeViews.view(attempt);
        });
    }
}
