package com.ruoyi.interview.application.evaluation.internal;

import com.ruoyi.interview.application.agent.evidence.EvidenceExtractorPort;
import com.ruoyi.interview.application.agent.judge.RubricJudgePort;
import com.ruoyi.interview.application.agent.report.ReportComposerPort;
import com.ruoyi.interview.application.evaluation.EvaluationRepository;
import com.ruoyi.interview.application.evaluation.EvaluationSourceAccessPort;
import com.ruoyi.interview.application.evaluation.RunEvaluationPipeline;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.evaluation.EvaluationReport;
import com.ruoyi.interview.domain.evaluation.EvaluationRun;
import com.ruoyi.interview.domain.evaluation.ReportVersion;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

/** Provider calls stay outside transactions；每个 state write 先在同一事务重验 Job lease。 */
public final class DefaultRunEvaluationPipeline implements RunEvaluationPipeline {

    private final EvaluationRepository repository;
    private final EvaluationSourceAccessPort sourceAccess;
    private final EvidenceExtractorPort evidenceExtractor;
    private final RubricJudgePort rubricJudge;
    private final ReportComposerPort reportComposer;
    private final JobPort jobs;
    private final IdGeneratorPort idGenerator;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultRunEvaluationPipeline(EvaluationRepository repository,
                                        EvaluationSourceAccessPort sourceAccess,
                                        EvidenceExtractorPort evidenceExtractor,
                                        RubricJudgePort rubricJudge,
                                        ReportComposerPort reportComposer,
                                        JobPort jobs,
                                        IdGeneratorPort idGenerator,
                                        DomainEventPort domainEvents,
                                        TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.sourceAccess = java.util.Objects.requireNonNull(sourceAccess);
        this.evidenceExtractor = java.util.Objects.requireNonNull(evidenceExtractor);
        this.rubricJudge = java.util.Objects.requireNonNull(rubricJudge);
        this.reportComposer = java.util.Objects.requireNonNull(reportComposer);
        this.jobs = java.util.Objects.requireNonNull(jobs);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        EvaluationRun initial = loadAndCheckLease(command);
        String sourceContentRef = sourceAccess.loadContentReference(initial.tenantId(), initial.userId(),
                initial.sourceRef());
        transitionToExtracting(command);
        initial = loadAndCheckLease(command);

        var evidenceResult = evidenceExtractor.extract(new EvidenceExtractorPort.Request(
                initial.tenantId(), initial.id(), initial.sourceRef().answerVersionId(),
                initial.sourceRef().answerHash(), initial.policySnapshot().evidencePin(),
                initial.policySnapshot().providerPolicy(), sourceContentRef,
                command.context().correlationId()));
        if (!evidenceResult.schemaValidated()) {
            return manualReview(command, "EVIDENCE_SCHEMA_INVALID");
        }
        attachEvidence(command, evidenceResult.bundle());
        EvaluationRun judging = loadAndCheckLease(command);

        var judgeResult = rubricJudge.judge(new RubricJudgePort.Request(
                judging.tenantId(), judging.id(), evidenceResult.bundle(),
                judging.policySnapshot().judgePin(), judging.policySnapshot().providerPolicy(),
                judging.policySnapshot().rubricVersionId(), command.context().correlationId()));
        if (!judgeResult.schemaValidated()) {
            return manualReview(command, "RUBRIC_SCHEMA_INVALID");
        }
        ResourceId evaluationVersionId = idGenerator.nextResourceId();
        attachJudgement(command, judgeResult.judgement(), evaluationVersionId);
        EvaluationRun composing = loadAndCheckLease(command);

        var reportResult = reportComposer.compose(new ReportComposerPort.Request(
                composing.tenantId(), composing.id(), evaluationVersionId,
                evidenceResult.bundle(), judgeResult.judgement(),
                composing.policySnapshot().reportPin(), composing.policySnapshot().providerPolicy(),
                command.context().correlationId()));
        if (!reportResult.schemaValidated()) {
            return manualReview(command, "REPORT_SCHEMA_INVALID");
        }
        validateReportReferences(reportResult.composition(), evidenceResult.bundle(), judgeResult.judgement());
        return publishReport(command, reportResult.composition());
    }

    private EvaluationRun loadAndCheckLease(Command command) {
        return transaction.required(() -> loadAndCheckLeaseInCurrentTransaction(command));
    }

    private EvaluationRun loadAndCheckLeaseInCurrentTransaction(Command command) {
        var job = jobs.find(command.execution().tenantId(), command.execution().jobId())
                .orElseThrow(() -> notFound("evaluation job was not found"));
        job.requireExecutionLease(command.execution().workerId(), command.execution().attemptNo(),
                command.context().requestedAt(), command.execution().expectedJobVersion());
        return repository.findRun(command.execution().tenantId(), command.evaluationId())
                .orElseThrow(() -> notFound("evaluation was not found"));
    }

    private void transitionToExtracting(Command command) {
        transaction.required(() -> {
            EvaluationRun run = loadAndCheckLeaseInCurrentTransaction(command);
            run.start(run.version(), command.context().eventContext());
            repository.saveRun(run);
            domainEvents.append(run.pullDomainEvents());
        });
    }

    private void attachEvidence(Command command, com.ruoyi.interview.domain.evaluation.EvidenceBundle bundle) {
        transaction.required(() -> {
            EvaluationRun run = loadAndCheckLeaseInCurrentTransaction(command);
            run.attachEvidence(bundle, run.version(), command.context().eventContext());
            repository.saveRun(run);
            domainEvents.append(run.pullDomainEvents());
        });
    }

    private void attachJudgement(Command command,
                                 com.ruoyi.interview.domain.evaluation.RubricScore judgement,
                                 ResourceId evaluationVersionId) {
        transaction.required(() -> {
            EvaluationRun run = loadAndCheckLeaseInCurrentTransaction(command);
            run.attachRubricJudgement(judgement, evaluationVersionId, run.version(),
                    command.context().eventContext());
            repository.saveRun(run);
            domainEvents.append(run.pullDomainEvents());
        });
    }

    private Result manualReview(Command command, String reasonCode) {
        return transaction.required(() -> {
            EvaluationRun run = loadAndCheckLeaseInCurrentTransaction(command);
            run.requireManualReview(reasonCode, run.version(), command.context().eventContext());
            repository.saveRun(run);
            domainEvents.append(run.pullDomainEvents());
            return result(run);
        });
    }

    private Result publishReport(Command command,
                                 com.ruoyi.interview.domain.evaluation.ReportComposition composition) {
        return transaction.required(() -> {
            EvaluationRun run = loadAndCheckLeaseInCurrentTransaction(command);
            ResourceId reportId = idGenerator.nextResourceId();
            ReportVersion reportVersion = new ReportVersion(idGenerator.nextResourceId(),
                    run.evaluationVersionId().orElseThrow(), run.policySnapshot().reportPin(),
                    composition, command.context().requestedAt());
            boolean partial = run.rubricJudgement().orElseThrow().dimensions().stream()
                    .anyMatch(com.ruoyi.interview.domain.evaluation.RubricDimensionScore::insufficientEvidence);
            EvaluationReport report = EvaluationReport.publish(reportId, run.tenantId(), run.userId(), run.id(),
                    run.sourceRef().interviewId(), reportVersion, partial, command.context().eventContext());
            run.complete(report.id(), run.version(), command.context().eventContext());
            repository.saveReport(report);
            repository.saveRun(run);
            domainEvents.append(report.pullDomainEvents());
            domainEvents.append(run.pullDomainEvents());
            return result(run);
        });
    }

    private static void validateReportReferences(
            com.ruoyi.interview.domain.evaluation.ReportComposition composition,
            com.ruoyi.interview.domain.evaluation.EvidenceBundle evidence,
            com.ruoyi.interview.domain.evaluation.RubricScore judgement) {
        var evidenceIds = new HashSet<>(evidence.spans().stream().map(
                com.ruoyi.interview.domain.evaluation.EvidenceItem::evidenceId).toList());
        var dimensionIds = new HashSet<>(judgement.dimensions().stream().map(
                com.ruoyi.interview.domain.evaluation.RubricDimensionScore::dimensionId).toList());
        boolean valid = composition.sections().stream().allMatch(section ->
                evidenceIds.containsAll(section.evidenceRefs())
                        && dimensionIds.containsAll(section.judgementRefs()));
        if (!valid) {
            throw new ApplicationException(ApplicationErrorCode.PROVIDER_BAD_RESPONSE,
                    "report composition contains unknown references", false,
                    Map.of("reasonCode", "REPORT_REFERENCE_INVALID"));
        }
    }

    private static Result result(EvaluationRun run) {
        return new Result(run.id(), run.status(), run.stage(), run.reportId());
    }

    private static ApplicationException notFound(String message) {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND, message, false, Map.of());
    }
}
