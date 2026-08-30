package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.InterviewSessionSnapshot;
import com.ruoyi.interview.application.interview.ProgressInterview;
import com.ruoyi.interview.application.interview.InterviewAgentCandidate;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.interview.QuestionPrompt;
import com.ruoyi.interview.domain.interview.SessionState;
import com.ruoyi.interview.domain.interview.TurnKind;
import com.ruoyi.interview.domain.interview.TurnState;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Map;
import java.util.Optional;

/**
 * MVP 采用已确认计划中的不可变题目顺序同步推进，不生成动态追问。
 * 动态 Agent/Worker 路线恢复后可替换本实现，不改变 REST 会话契约。
 */
public final class DefaultProgressInterview implements ProgressInterview {
    private final InterviewRepository repository;
    private final PublishedQuestionPort questions;
    private final TenantId publicTenantId;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort ids;
    private final DomainEventPort events;
    private final TransactionPort transaction;
    private final InterviewSnapshotFactory snapshots;
    private final InterviewAgentCandidate agent;
    private final com.ruoyi.interview.application.voice.port.ContentDigestPort digest;

    public DefaultProgressInterview(
            InterviewRepository repository,
            PublishedQuestionPort questions,
            TenantId publicTenantId,
            ActivePrincipalGuard principal,
            IdGeneratorPort ids,
            DomainEventPort events,
            TransactionPort transaction,
            InterviewRecoveryProjectionPort projections,
            InterviewAgentCandidate agent,
            com.ruoyi.interview.application.voice.port.ContentDigestPort digest
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.questions = java.util.Objects.requireNonNull(questions);
        this.publicTenantId = java.util.Objects.requireNonNull(publicTenantId);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.ids = java.util.Objects.requireNonNull(ids);
        this.events = java.util.Objects.requireNonNull(events);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.snapshots = new InterviewSnapshotFactory(projections);
        this.agent = java.util.Objects.requireNonNull(agent);
        this.digest = java.util.Objects.requireNonNull(digest);
    }

    @Override
    public InterviewSessionSnapshot handle(Command command) {
        var candidate = prepareAgentCandidate(command);
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            var session = repository.findSession(owner.tenantId(), command.sessionId())
                    .orElseThrow(DefaultProgressInterview::notFound);
            if (!session.userId().equals(owner.userId())) throw notFound();
            session.version().requireMatches(command.expectedVersion());

            if (session.state() == SessionState.COMPLETING) {
                session.complete(session.version(), command.context().eventContext());
                save(session);
                return snapshots.create(session, owner);
            }
            if (session.state() != SessionState.IN_PROGRESS) {
                return snapshots.create(session, owner);
            }

            var current = session.turns().isEmpty()
                    ? Optional.<com.ruoyi.interview.domain.interview.InterviewTurn>empty()
                    : Optional.of(session.turns().get(session.turns().size() - 1));
            if (current.isPresent() && (current.orElseThrow().state() == TurnState.PLANNED
                    || current.orElseThrow().state() == TurnState.QUESTION_COMMITTED)) {
                return snapshots.create(session, owner);
            }

            if (candidate.isPresent() && current.isPresent()
                    && current.orElseThrow().state() == TurnState.ANSWER_CONFIRMED
                    && current.orElseThrow().kind() == TurnKind.PRIMARY) {
                String followUp = candidate.orElseThrow();
                var parent = current.orElseThrow();
                var turnId = ids.nextResourceId();
                session.openTurn(turnId, parent.plannedQuestion(), TurnKind.FOLLOW_UP, parent.id(),
                        session.version(), command.context().eventContext());
                // openTurn 与 commitQuestion 各自推进一次聚合版本；按状态机原子步骤持久化，
                // 不能把 vN -> vN+2 合并成一次乐观锁更新。
                save(session);
                session.commitQuestion(turnId, new QuestionPrompt(followUp, digest.digest(followUp),
                                Optional.of(new com.ruoyi.interview.domain.platform.PromptRef(
                                        new com.ruoyi.interview.domain.platform.ImmutableVersionRef(
                                                com.ruoyi.interview.domain.platform.ResourceId.of("interview-agent-prompt-v1"), 1,
                                                "interview-agent-prompt-v1"))),
                                Optional.of(new com.ruoyi.interview.domain.platform.SchemaRef(
                                        new com.ruoyi.interview.domain.platform.ImmutableVersionRef(
                                                com.ruoyi.interview.domain.platform.ResourceId.of("interview-agent-schema-v1"), 1,
                                                "interview-agent-schema-v1")))),
                        session.version(), command.context().eventContext());
                save(session);
                return snapshots.create(session, owner);
            }

            int primaryCount = (int) session.turns().stream()
                    .filter(turn -> turn.kind() == TurnKind.PRIMARY).count();
            int nextPosition = primaryCount + 1;
            if (nextPosition > session.planReference().questionCount()) {
                session.beginCompletion(session.version(), command.context().eventContext());
                save(session);
                session.complete(session.version(), command.context().eventContext());
                save(session);
                return snapshots.create(session, owner);
            }

            var planned = session.planReference().questionAtPosition(nextPosition);
            var published = questions.findPublishedVersion(publicTenantId,
                            planned.questionVersion(), planned.rubricVersion())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "planned published question was not found", false,
                            Map.of("position", Integer.toString(nextPosition))));
            var turnId = ids.nextResourceId();
            session.openTurn(turnId, planned, TurnKind.PRIMARY, null,
                    session.version(), command.context().eventContext());
            save(session);
            session.commitQuestion(turnId,
                    new QuestionPrompt(published.stem(), published.questionVersion().contentHash(),
                            Optional.empty(), Optional.empty()),
                    session.version(), command.context().eventContext());
            save(session);
            return snapshots.create(session, owner);
        });
    }

    private Optional<String> prepareAgentCandidate(Command command) {
        var owner = principal.requireActive(command.context());
        var session = repository.findSession(owner.tenantId(), command.sessionId()).orElse(null);
        if (session == null || !session.userId().equals(owner.userId()) || session.turns().isEmpty()) {
            return Optional.empty();
        }
        var turn = session.turns().get(session.turns().size() - 1);
        if (turn.state() != TurnState.ANSWER_CONFIRMED || turn.kind() != TurnKind.PRIMARY
                || turn.plannedQuestion().followUpBudget() <= 0) return Optional.empty();
        long used = session.turns().stream().filter(item -> item.kind() != TurnKind.PRIMARY).count();
        int remaining = Math.max(0, session.planReference().followUpBudget() - (int) used);
        if (remaining == 0) return Optional.empty();
        var proposal = agent.propose(new InterviewAgentCandidate.Query(owner.tenantId(),
                turn.questionPrompt().orElseThrow().text(), turn.answerVersion().orElseThrow().text(),
                remaining, command.context()));
        return proposal.action() == InterviewAgentCandidate.Result.Action.FOLLOW_UP
                ? proposal.questionText() : Optional.empty();
    }

    private void save(com.ruoyi.interview.domain.interview.InterviewSession session) {
        repository.saveSession(session);
        events.append(session.pullDomainEvents());
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "interview session was not found", false, Map.of());
    }
}
