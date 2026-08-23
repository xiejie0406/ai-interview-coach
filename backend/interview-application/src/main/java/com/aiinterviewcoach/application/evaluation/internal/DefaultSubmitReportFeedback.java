package com.aiinterviewcoach.application.evaluation.internal;

import com.aiinterviewcoach.application.evaluation.EvaluationRepository;
import com.aiinterviewcoach.application.evaluation.FeedbackContentPort;
import com.aiinterviewcoach.application.evaluation.SubmitReportFeedback;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.evaluation.FeedbackNote;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.Map;
import java.util.Optional;

/** 以 evaluationId 解析 owner-scoped Report，追加反馈，不改写 Evaluation/ReportVersion。 */
public final class DefaultSubmitReportFeedback implements SubmitReportFeedback {

    private final EvaluationRepository repository;
    private final FeedbackContentPort content;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultSubmitReportFeedback(EvaluationRepository repository, FeedbackContentPort content,
                                       IdGeneratorPort idGenerator, IdempotencyGuard idempotency,
                                       DomainEventPort domainEvents, TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.content = java.util.Objects.requireNonNull(content);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var principal = command.context().requirePrincipal();
            var decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "evaluation.feedback", command.requestHash(), command.context()));
            var report = repository.findReportByEvaluation(principal.tenantId(), command.evaluationId())
                    .orElseThrow(() -> notFound());
            if (!report.userId().equals(principal.userId())) {
                throw notFound();
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String feedbackId = decision.resourceReferences().get("feedbackId");
                if (feedbackId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "feedback replay has no feedback reference", false, Map.of());
                }
                return new Result(ResourceId.of(feedbackId), report.id(), report.version());
            }
            ResourceId feedbackId = idGenerator.nextResourceId();
            Optional<String> commentRef = command.comment().map(value ->
                    content.store(principal.tenantId(), principal.userId(), command.evaluationId(),
                            feedbackId, value));
            FeedbackNote note = new FeedbackNote(feedbackId, principal.userId(), command.type(),
                    commentRef, command.context().requestedAt());
            report.appendFeedback(note, report.version(), command.context().eventContext());
            repository.saveReport(report);
            domainEvents.append(report.pullDomainEvents());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "evaluation.feedback", command.requestHash(),
                    Map.of("feedbackId", feedbackId.value(), "reportId", report.id().value()),
                    201, command.context()));
            return new Result(feedbackId, report.id(), report.version());
        });
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "evaluation report was not found", false, Map.of());
    }
}
