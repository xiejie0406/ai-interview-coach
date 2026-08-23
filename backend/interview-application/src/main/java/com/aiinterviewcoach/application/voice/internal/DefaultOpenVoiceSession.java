package com.aiinterviewcoach.application.voice.internal;

import com.aiinterviewcoach.application.governance.port.ConsentQueryPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.voice.OpenVoiceSession;
import com.aiinterviewcoach.application.voice.port.InterviewVoiceAccessPort;
import com.aiinterviewcoach.application.voice.port.VoiceCapabilityPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.application.voice.port.VoiceSessionTicketPort;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.voice.AudioArtifact;
import com.aiinterviewcoach.domain.voice.AudioPurpose;
import com.aiinterviewcoach.domain.voice.VoiceTurnExecution;
import com.aiinterviewcoach.domain.voice.VoiceTurnState;

import java.time.Duration;

/** 创建 handle 前完成 owner、状态、同意、能力和 codec 门禁；不触发麦克风或 Provider。 */
public final class DefaultOpenVoiceSession implements OpenVoiceSession {

    private final VoiceRepository repository;
    private final InterviewVoiceAccessPort interviewAccess;
    private final ConsentQueryPort consentQuery;
    private final VoiceCapabilityPort capabilityPort;
    private final VoiceSessionTicketPort ticketPort;
    private final IdGeneratorPort idGenerator;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;
    private final Duration inputRetention;
    private final Duration ticketTtl;

    public DefaultOpenVoiceSession(VoiceRepository repository,
                                   InterviewVoiceAccessPort interviewAccess,
                                   ConsentQueryPort consentQuery,
                                   VoiceCapabilityPort capabilityPort,
                                   VoiceSessionTicketPort ticketPort,
                                   IdGeneratorPort idGenerator,
                                   DomainEventPort domainEvents,
                                   TransactionPort transaction,
                                   Duration inputRetention,
                                   Duration ticketTtl) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.interviewAccess = java.util.Objects.requireNonNull(interviewAccess);
        this.consentQuery = java.util.Objects.requireNonNull(consentQuery);
        this.capabilityPort = java.util.Objects.requireNonNull(capabilityPort);
        this.ticketPort = java.util.Objects.requireNonNull(ticketPort);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
        this.inputRetention = requirePositive(inputRetention, "inputRetention");
        this.ticketTtl = requirePositive(ticketTtl, "ticketTtl");
    }

    @Override
    public Result handle(Command command) {
        var principal = command.context().requirePrincipal();
        var access = interviewAccess.inspect(principal.tenantId(), principal.userId(),
                command.interviewId(), command.turnId());
        if (!access.allowed()) {
            throw new DomainException(DomainErrorCode.OWNERSHIP_DENIED,
                    "voice access is not allowed for current interview turn");
        }
        access.sessionVersion().requireMatches(command.expectedSessionVersion());
        var consentRecordId = requireConsent(principal.tenantId(), principal.userId(), command);
        var capability = capabilityPort.current(principal.tenantId(), principal.userId());
        if (!capability.enabled()) {
            throw new DomainException(DomainErrorCode.POLICY_DENIED,
                    "voice capability is not enabled");
        }
        if (!capability.supportedCodecs().contains(command.codec())) {
            throw new DomainException(DomainErrorCode.INVALID_ARGUMENT,
                    "requested voice codec is not supported");
        }

        var artifactId = idGenerator.nextResourceId();
        var existing = repository.findExecution(principal.tenantId(), command.interviewId(), command.turnId());
        var execution = existing.orElseGet(() -> VoiceTurnExecution.idle(
                idGenerator.nextResourceId(), principal.tenantId(), command.interviewId(), command.turnId()));
        if (execution.state() != VoiceTurnState.IDLE && execution.state() != VoiceTurnState.DEGRADED) {
            throw new DomainException(DomainErrorCode.SESSION_ALREADY_ACTIVE,
                    "voice execution is already active for current turn");
        }
        if (execution.socketGeneration() == Long.MAX_VALUE) {
            throw new DomainException(DomainErrorCode.INVALID_STATE,
                    "voice socket generation is exhausted");
        }
        long nextGeneration = execution.socketGeneration() + 1;
        var ticketExpiresAt = command.context().requestedAt().plus(ticketTtl);
        var handle = ticketPort.issue(new VoiceSessionTicketPort.IssueRequest(
                principal.tenantId(), principal.userId(), command.interviewId(), command.turnId(),
                execution.id(), artifactId, nextGeneration, command.codec(), ticketExpiresAt));
        if (!handle.expiresAt().equals(ticketExpiresAt) || !handle.codec().equals(command.codec())) {
            throw new DomainException(DomainErrorCode.INVALID_STATE,
                    "issued voice handle does not match requested policy");
        }

        transaction.required(() -> {
            var artifact = AudioArtifact.create(artifactId, principal.tenantId(), command.interviewId(),
                    command.turnId(), AudioPurpose.ANSWER_TRANSCRIPTION,
                    consentRecordId,
                    command.context().requestedAt().plus(inputRetention), command.context().eventContext());
            execution.startListening(artifactId, nextGeneration, execution.version());
            repository.saveArtifact(artifact);
            repository.saveExecution(execution);
            domainEvents.append(artifact.pullDomainEvents());
        });
        return new Result(handle, execution.id(), artifactId, execution.version(),
                nextGeneration, execution.lastServerSequence(), new OpenVoiceSession.FlowControl(
                1, capability.maximumInFlightChunks(), capability.maximumChunkBytes(),
                capability.maximumBufferedDurationMillis(), capability.maximumDurationSeconds(),
                capability.maximumBytes()));
    }

    private com.aiinterviewcoach.domain.platform.ResourceId requireConsent(
            com.aiinterviewcoach.domain.platform.TenantId tenantId,
            com.aiinterviewcoach.domain.platform.UserId userId,
            Command command) {
        var voiceDecision = consentQuery.current(tenantId, userId, ConsentPurpose.VOICE_CAPTURE,
                command.context().requestedAt());
        var modelDecision = consentQuery.current(tenantId, userId, ConsentPurpose.MODEL_PROCESSING,
                command.context().requestedAt());
        if (!voiceDecision.granted() || !modelDecision.granted()) {
            throw new DomainException(DomainErrorCode.CONSENT_REQUIRED,
                    "voice capture and model processing consent are required");
        }
        return voiceDecision.consentRecordId().orElseThrow(() -> new DomainException(
                DomainErrorCode.CONSENT_REQUIRED, "voice capture consent record is required"));
    }

    private static Duration requirePositive(Duration duration, String name) {
        java.util.Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
