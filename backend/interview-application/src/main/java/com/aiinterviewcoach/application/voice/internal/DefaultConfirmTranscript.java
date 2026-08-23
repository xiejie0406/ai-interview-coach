package com.aiinterviewcoach.application.voice.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.voice.ConfirmTranscript;
import com.aiinterviewcoach.application.voice.port.ConfirmedTranscriptAnswerPort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.application.voice.port.InterviewVoiceAccessPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.voice.TranscriptSource;
import com.aiinterviewcoach.domain.voice.TranscriptVersion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transcript 确认、不可变 Interview AnswerVersion、Turn 稳定点、next Job/Outbox 与幂等回执
 * 在同一个本地事务内提交；ASR final 永远不会被隐式当作回答。
 */
public final class DefaultConfirmTranscript implements ConfirmTranscript {

    private static final String OPERATION = "voice.transcript.confirm";

    private final VoiceRepository repository;
    private final ActivePrincipalGuard principal;
    private final IdGeneratorPort idGenerator;
    private final ContentDigestPort digestPort;
    private final InterviewVoiceAccessPort interviewAccess;
    private final ConfirmedTranscriptAnswerPort answerPort;
    private final IdempotencyGuard idempotency;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultConfirmTranscript(
            VoiceRepository repository,
            ActivePrincipalGuard principal,
            IdGeneratorPort idGenerator,
            ContentDigestPort digestPort,
            InterviewVoiceAccessPort interviewAccess,
            ConfirmedTranscriptAnswerPort answerPort,
            IdempotencyGuard idempotency,
            DomainEventPort domainEvents,
            TransactionPort transaction
    ) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.digestPort = java.util.Objects.requireNonNull(digestPort);
        this.interviewAccess = java.util.Objects.requireNonNull(interviewAccess);
        this.answerPort = java.util.Objects.requireNonNull(answerPort);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String correctionHash = command.correctedText()
                    .map(digestPort::digest)
                    .orElse("NO_CORRECTION");
            String requestHash = ServerSideDigest.sha256(
                    OPERATION,
                    command.transcriptId().value(),
                    command.transcriptVersionId().value(),
                    correctionHash,
                    Boolean.toString(command.lowConfidenceAcknowledged()),
                    Long.toString(command.expectedVersion().value()));
            IdempotencyGuard.Decision decision = idempotency.begin(
                    new IdempotencyGuard.BeginCommand(OPERATION, requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return replay(decision.resourceReferences());
            }
            rejectUnexpectedDecision(decision);

            var transcript = repository.findTranscript(owner.tenantId(), command.transcriptId())
                    .orElseThrow(DefaultConfirmTranscript::notFound);
            var access = interviewAccess.inspect(owner.tenantId(), owner.userId(),
                    transcript.sessionId(), transcript.turnId());
            if (!access.allowed()) {
                throw notFound();
            }
            var execution = repository.findExecution(owner.tenantId(),
                            transcript.sessionId(), transcript.turnId())
                    .orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "voice execution for transcript confirmation was not found", false,
                            Map.of("transcriptId", transcript.id().value())));
            if (execution.transcriptId().filter(transcript.id()::equals).isEmpty()) {
                throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "voice execution does not reference the transcript", false,
                        Map.of("transcriptId", transcript.id().value(),
                                "voiceExecutionId", execution.id().value()));
            }
            transcript.version().requireMatches(command.expectedVersion());
            TranscriptVersion latest = transcript.latestVersion().orElseThrow(() -> new DomainException(
                    DomainErrorCode.INVALID_STATE, "transcript has no final version"));
            if (!latest.id().equals(command.transcriptVersionId())) {
                throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                        "only latest transcript version can be confirmed or corrected");
            }
            if (!latest.lowConfidenceSpans().isEmpty()
                    && !command.lowConfidenceAcknowledged()
                    && command.correctedText().isEmpty()) {
                throw new DomainException(DomainErrorCode.POLICY_DENIED,
                        "low confidence transcript ranges require acknowledgement or correction");
            }

            TranscriptVersion confirmedVersion = latest;
            if (command.correctedText().isPresent()) {
                String corrected = command.correctedText().orElseThrow();
                confirmedVersion = new TranscriptVersion(
                        idGenerator.nextResourceId(),
                        owner.tenantId(),
                        transcript.id(),
                        latest.versionNo() + 1,
                        TranscriptSource.USER_CORRECTION,
                        corrected,
                        digestPort.digest(corrected),
                        latest.language(),
                        latest.offsetUnit(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(owner.userId()),
                        Optional.of(latest.id()),
                        command.context().requestedAt());
                transcript.appendCorrection(confirmedVersion, transcript.version(),
                        command.context().eventContext());
            }
            transcript.confirm(confirmedVersion.id(), owner.userId(), transcript.version(),
                    command.context().eventContext());
            execution.markThinking(confirmedVersion.id(), execution.version());

            ConfirmedTranscriptAnswerPort.Result answer = answerPort.commit(
                    new ConfirmedTranscriptAnswerPort.Command(
                            transcript.sessionId(),
                            transcript.turnId(),
                            confirmedVersion.id(),
                            confirmedVersion.text(),
                            command.context()));
            repository.saveTranscript(transcript);
            repository.saveExecution(execution);
            domainEvents.append(transcript.pullDomainEvents());

            Result result = new Result(
                    transcript.id(),
                    confirmedVersion.id(),
                    answer.answerVersionId(),
                    answer.nextStepOperation(),
                    transcript.version());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    OPERATION,
                    requestHash,
                    idempotencyReferences(result),
                    201,
                    command.context()));
            return result;
        });
    }

    private static Map<String, String> idempotencyReferences(Result result) {
        LinkedHashMap<String, String> references = new LinkedHashMap<>();
        references.put("transcriptId", result.transcriptId().value());
        references.put("confirmedTranscriptVersionId", result.confirmedTranscriptVersionId().value());
        references.put("answerVersionId", result.answerVersionId().value());
        references.put("transcriptAggregateVersion", Long.toString(result.version().value()));
        OperationAccepted accepted = result.nextStepOperation();
        references.put("operationId", accepted.operationId().value());
        accepted.jobId().ifPresent(value -> references.put("jobId", value.value()));
        accepted.resourceId().ifPresent(value -> references.put("operationResourceId", value.value()));
        references.put("statusPath", accepted.statusPath());
        accepted.streamPath().ifPresent(value -> references.put("streamPath", value));
        references.put("acceptedAt", accepted.acceptedAt().toString());
        return Map.copyOf(references);
    }

    private static Result replay(Map<String, String> references) {
        ResourceId transcriptId = ResourceId.of(required(references, "transcriptId"));
        ResourceId confirmedVersionId = ResourceId.of(required(
                references, "confirmedTranscriptVersionId"));
        ResourceId answerVersionId = ResourceId.of(required(references, "answerVersionId"));
        long transcriptVersion;
        try {
            transcriptVersion = Long.parseLong(required(references, "transcriptAggregateVersion"));
        } catch (NumberFormatException exception) {
            throw consistency("transcriptAggregateVersion");
        }
        OperationAccepted accepted;
        try {
            accepted = new OperationAccepted(
                    ResourceId.of(required(references, "operationId")),
                    Optional.ofNullable(references.get("jobId")).map(ResourceId::of),
                    Optional.ofNullable(references.get("operationResourceId")).map(ResourceId::of),
                    required(references, "statusPath"),
                    Optional.ofNullable(references.get("streamPath")),
                    Instant.parse(required(references, "acceptedAt")));
        } catch (java.time.format.DateTimeParseException exception) {
            throw consistency("acceptedAt");
        }
        return new Result(transcriptId, confirmedVersionId, answerVersionId, accepted,
                new AggregateVersion(transcriptVersion));
    }

    private static void rejectUnexpectedDecision(IdempotencyGuard.Decision decision) {
        if (decision.type() == IdempotencyGuard.DecisionType.NEW) {
            return;
        }
        ApplicationErrorCode code = decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS
                ? ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS
                : ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE;
        throw new ApplicationException(code,
                "transcript confirmation idempotency decision rejected", true, Map.of());
    }

    private static String required(Map<String, String> references, String key) {
        String value = references.get(key);
        if (value == null || value.isBlank()) {
            throw consistency(key);
        }
        return value;
    }

    private static ApplicationException consistency(String referenceKey) {
        return new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                "transcript confirmation replay has incomplete references", false,
                Map.of("referenceKey", referenceKey));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "transcript was not found", false, Map.of());
    }
}
