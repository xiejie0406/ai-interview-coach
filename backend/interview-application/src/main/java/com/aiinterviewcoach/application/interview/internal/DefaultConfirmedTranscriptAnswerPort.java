package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.voice.port.ConfirmedTranscriptAnswerPort;
import com.aiinterviewcoach.domain.interview.InterviewAnswerSource;

import java.util.Optional;

/** Interview owner 对 Voice consumer-owned 原子提交端口的实现；由外层 Confirm Transcript 事务调用。 */
public final class DefaultConfirmedTranscriptAnswerPort implements ConfirmedTranscriptAnswerPort {

    private final ActivePrincipalGuard principal;
    private final InterviewAnswerCommitter committer;

    public DefaultConfirmedTranscriptAnswerPort(
            InterviewRepository repository,
            JobPort jobs,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            DomainEventPort domainEvents
    ) {
        this(repository, jobs, principal, idGenerator, domainEvents, 3);
    }

    public DefaultConfirmedTranscriptAnswerPort(
            InterviewRepository repository,
            JobPort jobs,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            DomainEventPort domainEvents,
            int maxAttempts
    ) {
        this.principal = java.util.Objects.requireNonNull(principal);
        this.committer = new InterviewAnswerCommitter(repository, jobs, idGenerator, domainEvents, maxAttempts);
    }

    @Override
    public Result commit(Command command) {
        var owner = principal.requireActive(command.context());
        var committed = committer.commit(
                owner,
                command.sessionId(),
                command.turnId(),
                Optional.empty(),
                // 现有领域名 CONFIRMED_TRANSCRIPT 表达公共链路中的 VOICE_TRANSCRIPT 来源。
                InterviewAnswerSource.CONFIRMED_TRANSCRIPT,
                command.confirmedText(),
                Optional.of(command.confirmedTranscriptVersionId()),
                Optional.empty(),
                command.context());
        return new Result(committed.answerVersionId(), committed.nextStepOperation(),
                committed.session().version());
    }
}
