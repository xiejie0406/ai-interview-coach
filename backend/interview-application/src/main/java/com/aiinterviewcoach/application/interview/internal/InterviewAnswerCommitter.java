package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.interview.InterviewAnswerSource;
import com.aiinterviewcoach.domain.interview.InterviewAnswerVersion;
import com.aiinterviewcoach.domain.interview.InterviewSession;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.Job;
import com.aiinterviewcoach.domain.platform.PrincipalRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AnswerVersion、Turn 稳定点、next Job 与 Outbox 的单事务工作单元；本类本身不打开事务或声明幂等结果。
 */
final class InterviewAnswerCommitter {

    private final InterviewRepository repository;
    private final JobPort jobs;
    private final IdGeneratorPort idGenerator;
    private final DomainEventPort domainEvents;
    private final int maxAttempts;

    InterviewAnswerCommitter(
            InterviewRepository repository,
            JobPort jobs,
            IdGeneratorPort idGenerator,
            DomainEventPort domainEvents,
            int maxAttempts
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    Commit commit(
            PrincipalRef owner,
            ResourceId sessionId,
            ResourceId turnId,
            Optional<Integer> expectedTurnSequence,
            InterviewAnswerSource source,
            String answerText,
            Optional<ResourceId> confirmedTranscriptVersionId,
            Optional<AggregateVersion> expectedSessionVersion,
            OperationContext context
    ) {
        java.util.Objects.requireNonNull(owner);
        var session = repository.findSession(owner.tenantId(), sessionId).orElseThrow(
                InterviewAnswerCommitter::notFound);
        if (!session.userId().equals(owner.userId())) {
            throw notFound();
        }
        var turn = session.turns().stream().filter(item -> item.id().equals(turnId)).findFirst()
                .orElseThrow(() -> new DomainException(DomainErrorCode.STALE_TURN,
                        "interview turn is no longer current"));
        expectedTurnSequence.ifPresent(sequence -> {
            if (turn.sequence() != sequence) {
                throw new DomainException(DomainErrorCode.STALE_TURN,
                        "interview turn sequence is stale");
            }
        });
        AggregateVersion versionToCommit = expectedSessionVersion.orElse(session.version());
        var answer = new InterviewAnswerVersion(
                idGenerator.nextResourceId(),
                owner.tenantId(),
                session.id(),
                turnId,
                1,
                source,
                answerText,
                ServerSideDigest.sha256(answerText),
                confirmedTranscriptVersionId,
                owner.userId(),
                context.requestedAt(),
                Optional.empty());
        session.confirmAnswer(turnId, answer, versionToCommit, context.eventContext());

        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.id().value());
        payload.put("turnId", turnId.value());
        payload.put("answerVersionId", answer.id().value());
        confirmedTranscriptVersionId.ifPresent(value ->
                payload.put("confirmedTranscriptVersionId", value.value()));
        // 一个 Session 会产生多个回答步骤；业务操作必须按不可变 AnswerVersion 去重，
        // 复用 sessionId 会与“开始面试”的首个 INTERVIEW_STEP Job 冲突。
        var job = Job.schedule(idGenerator.nextResourceId(), owner.tenantId(), "INTERVIEW_STEP", answer.id(),
                payload, maxAttempts, context.requestedAt(), context.eventContext());
        repository.saveSession(session);
        jobs.save(job);
        domainEvents.append(session.pullDomainEvents());
        domainEvents.append(job.pullDomainEvents());
        OperationAccepted accepted = OperationAcceptedFactory.forJob(
                job.id(), session.id(), context.requestedAt());
        return new Commit(session, answer.id(), accepted);
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "interview session was not found", false, Map.of());
    }

    record Commit(
            InterviewSession session,
            ResourceId answerVersionId,
            OperationAccepted nextStepOperation
    ) {
        Commit {
            java.util.Objects.requireNonNull(session);
            java.util.Objects.requireNonNull(answerVersionId);
            java.util.Objects.requireNonNull(nextStepOperation);
        }
    }
}
