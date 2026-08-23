package com.aiinterviewcoach.domain.voice;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Optional;

/** WebSocket/语音执行状态；它不拥有 Interview Session 或 Turn 的业务裁决。 */
public final class VoiceTurnExecution {

    private final ResourceId id;
    private final TenantId tenantId;
    private final ResourceId sessionId;
    private final ResourceId turnId;
    private VoiceTurnState state;
    private ResourceId inputArtifactId;
    private ResourceId transcriptId;
    private ResourceId confirmedTranscriptVersionId;
    private ResourceId outputArtifactId;
    private long socketGeneration;
    private long lastClientSequence;
    private long lastServerSequence;
    private String degradationReason;
    private AggregateVersion version;

    private VoiceTurnExecution(ResourceId id, TenantId tenantId, ResourceId sessionId, ResourceId turnId,
                               VoiceTurnState state, AggregateVersion version) {
        this.id = DomainPreconditions.requireNonNull(id, "voiceTurnExecutionId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.sessionId = DomainPreconditions.requireNonNull(sessionId, "sessionId");
        this.turnId = DomainPreconditions.requireNonNull(turnId, "turnId");
        this.state = DomainPreconditions.requireNonNull(state, "voiceTurnState");
        this.version = DomainPreconditions.requireNonNull(version, "voiceTurnVersion");
    }

    public static VoiceTurnExecution idle(ResourceId id, TenantId tenantId,
                                          ResourceId sessionId, ResourceId turnId) {
        return new VoiceTurnExecution(id, tenantId, sessionId, turnId,
                VoiceTurnState.IDLE, AggregateVersion.initial());
    }

    public static VoiceTurnExecution rehydrate(
            ResourceId id, TenantId tenantId, ResourceId sessionId, ResourceId turnId,
            VoiceTurnState state, ResourceId inputArtifactId, ResourceId transcriptId,
            ResourceId confirmedTranscriptVersionId, ResourceId outputArtifactId,
            long socketGeneration, long lastClientSequence, long lastServerSequence,
            String degradationReason, AggregateVersion version) {
        VoiceTurnExecution execution = new VoiceTurnExecution(id, tenantId, sessionId, turnId, state, version);
        execution.inputArtifactId = inputArtifactId;
        execution.transcriptId = transcriptId;
        execution.confirmedTranscriptVersionId = confirmedTranscriptVersionId;
        execution.outputArtifactId = outputArtifactId;
        execution.socketGeneration = socketGeneration;
        execution.lastClientSequence = lastClientSequence;
        execution.lastServerSequence = lastServerSequence;
        execution.degradationReason = degradationReason;
        execution.assertConsistent();
        return execution;
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ResourceId sessionId() { return sessionId; }
    public ResourceId turnId() { return turnId; }
    public VoiceTurnState state() { return state; }
    public Optional<ResourceId> inputArtifactId() { return Optional.ofNullable(inputArtifactId); }
    public Optional<ResourceId> transcriptId() { return Optional.ofNullable(transcriptId); }
    public Optional<ResourceId> confirmedTranscriptVersionId() {
        return Optional.ofNullable(confirmedTranscriptVersionId);
    }
    public Optional<ResourceId> outputArtifactId() { return Optional.ofNullable(outputArtifactId); }
    public long socketGeneration() { return socketGeneration; }
    public long lastClientSequence() { return lastClientSequence; }
    public long lastServerSequence() { return lastServerSequence; }
    public Optional<String> degradationReason() { return Optional.ofNullable(degradationReason); }
    public AggregateVersion version() { return version; }

    public void startListening(ResourceId inputArtifactId, long nextSocketGeneration,
                               AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == VoiceTurnState.IDLE || state == VoiceTurnState.DEGRADED,
                DomainErrorCode.INVALID_STATE, "voice turn cannot start listening from current state");
        DomainPreconditions.require(nextSocketGeneration > socketGeneration, DomainErrorCode.VERSION_CONFLICT,
                "voice socket generation must increase");
        this.inputArtifactId = DomainPreconditions.requireNonNull(inputArtifactId, "inputArtifactId");
        socketGeneration = nextSocketGeneration;
        lastClientSequence = 0;
        lastServerSequence = 0;
        degradationReason = null;
        state = VoiceTurnState.LISTENING;
        version = version.next();
    }

    public void acceptClientSequence(long sequence, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == VoiceTurnState.LISTENING,
                DomainErrorCode.INVALID_STATE, "voice turn is not accepting audio");
        DomainPreconditions.require(sequence == lastClientSequence + 1, DomainErrorCode.VERSION_CONFLICT,
                "voice client sequence contains a gap or duplicate");
        lastClientSequence = sequence;
        version = version.next();
    }

    public void markTranscribing(AggregateVersion expectedVersion) {
        transition(VoiceTurnState.LISTENING, VoiceTurnState.TRANSCRIBING, expectedVersion);
    }

    public void markConfirming(ResourceId transcriptId, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == VoiceTurnState.TRANSCRIBING,
                DomainErrorCode.INVALID_STATE, "voice turn is not transcribing");
        this.transcriptId = DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
        state = VoiceTurnState.CONFIRMING;
        version = version.next();
    }

    public void markThinking(ResourceId confirmedTranscriptVersionId, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == VoiceTurnState.CONFIRMING,
                DomainErrorCode.INVALID_STATE, "voice turn is not awaiting transcript confirmation");
        this.confirmedTranscriptVersionId = DomainPreconditions.requireNonNull(
                confirmedTranscriptVersionId, "confirmedTranscriptVersionId");
        state = VoiceTurnState.THINKING;
        version = version.next();
    }

    public void markSpeaking(ResourceId outputArtifactId, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == VoiceTurnState.THINKING,
                DomainErrorCode.INVALID_STATE, "voice turn is not ready for speech output");
        this.outputArtifactId = DomainPreconditions.requireNonNull(outputArtifactId, "outputArtifactId");
        state = VoiceTurnState.SPEAKING;
        version = version.next();
    }

    public void emitServerSequence(long sequence, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state != VoiceTurnState.IDLE && state != VoiceTurnState.CANCELLED,
                DomainErrorCode.INVALID_STATE, "inactive voice turn cannot emit protocol frames");
        DomainPreconditions.require(sequence == lastServerSequence + 1, DomainErrorCode.VERSION_CONFLICT,
                "voice server sequence contains a gap or duplicate");
        lastServerSequence = sequence;
        version = version.next();
    }

    public void finishPlayback(AggregateVersion expectedVersion) {
        transition(VoiceTurnState.SPEAKING, VoiceTurnState.IDLE, expectedVersion);
    }

    public void degrade(String reasonCode, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state != VoiceTurnState.CANCELLED,
                DomainErrorCode.INVALID_STATE, "cancelled voice turn cannot degrade");
        degradationReason = DomainPreconditions.requireText(reasonCode, "voiceDegradationReason");
        state = VoiceTurnState.DEGRADED;
        version = version.next();
    }

    public void cancel(AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state != VoiceTurnState.CANCELLED,
                DomainErrorCode.INVALID_STATE, "voice turn is already cancelled");
        degradationReason = null;
        state = VoiceTurnState.CANCELLED;
        version = version.next();
    }

    private void transition(VoiceTurnState from, VoiceTurnState to, AggregateVersion expectedVersion) {
        expected(expectedVersion);
        DomainPreconditions.require(state == from, DomainErrorCode.INVALID_STATE,
                "voice turn cannot transition from current state");
        state = to;
        version = version.next();
    }

    private void expected(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void assertConsistent() {
        DomainPreconditions.require(socketGeneration >= 0 && lastClientSequence >= 0 && lastServerSequence >= 0,
                DomainErrorCode.INVALID_STATE, "voice sequence facts must not be negative");
        DomainPreconditions.require((state == VoiceTurnState.DEGRADED) == (degradationReason != null),
                DomainErrorCode.INVALID_STATE, "voice degradation reason is inconsistent with state");
        if (state == VoiceTurnState.LISTENING || state == VoiceTurnState.TRANSCRIBING
                || state == VoiceTurnState.CONFIRMING || state == VoiceTurnState.THINKING
                || state == VoiceTurnState.SPEAKING) {
            DomainPreconditions.requireNonNull(inputArtifactId, "inputArtifactId");
        }
        if (state == VoiceTurnState.CONFIRMING || state == VoiceTurnState.THINKING
                || state == VoiceTurnState.SPEAKING) {
            DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
        }
        if (state == VoiceTurnState.THINKING || state == VoiceTurnState.SPEAKING) {
            DomainPreconditions.requireNonNull(confirmedTranscriptVersionId, "confirmedTranscriptVersionId");
        }
        if (state == VoiceTurnState.SPEAKING) {
            DomainPreconditions.requireNonNull(outputArtifactId, "outputArtifactId");
        }
    }
}
