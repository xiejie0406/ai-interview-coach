package com.ruoyi.interview.domain.voice;

import com.ruoyi.interview.domain.platform.AggregateRoot;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** 音频对象的业务生命周期；对象存储 adapter 不能直接推进该状态。 */
public final class AudioArtifact extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final ResourceId sessionId;
    private final ResourceId turnId;
    private final AudioPurpose purpose;
    private final ResourceId consentRecordId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private AudioArtifactState state;
    private String codec;
    private Integer sampleRate;
    private Integer channelCount;
    private Long bytes;
    private Long durationMillis;
    private StorageObjectRef storageObjectRef;
    private String contentHash;
    private ResourceId providerInvocationId;
    private Instant deleteQueuedAt;
    private Instant deletedAt;
    private String failureCode;
    private AggregateVersion version;

    private AudioArtifact(
            ResourceId id,
            TenantId tenantId,
            ResourceId sessionId,
            ResourceId turnId,
            AudioPurpose purpose,
            ResourceId consentRecordId,
            Instant createdAt,
            Instant expiresAt,
            AudioArtifactState state,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "audioArtifactId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.sessionId = DomainPreconditions.requireNonNull(sessionId, "sessionId");
        this.turnId = DomainPreconditions.requireNonNull(turnId, "turnId");
        this.purpose = DomainPreconditions.requireNonNull(purpose, "audioPurpose");
        this.consentRecordId = DomainPreconditions.requireNonNull(consentRecordId, "consentRecordId");
        this.createdAt = DomainPreconditions.requireNonNull(createdAt, "audioArtifactCreatedAt");
        this.expiresAt = DomainPreconditions.requireNonNull(expiresAt, "audioArtifactExpiresAt");
        DomainPreconditions.require(expiresAt.isAfter(createdAt), DomainErrorCode.INVALID_ARGUMENT,
                "audio artifact expiry must be after creation");
        this.state = DomainPreconditions.requireNonNull(state, "audioArtifactState");
        this.version = DomainPreconditions.requireNonNull(version, "audioArtifactVersion");
    }

    public static AudioArtifact create(
            ResourceId id,
            TenantId tenantId,
            ResourceId sessionId,
            ResourceId turnId,
            AudioPurpose purpose,
            ResourceId consentRecordId,
            Instant expiresAt,
            EventContext context
    ) {
        DomainPreconditions.requireNonNull(context, "eventContext");
        AudioArtifact artifact = new AudioArtifact(id, tenantId, sessionId, turnId, purpose,
                consentRecordId, context.occurredAt(), expiresAt, AudioArtifactState.CREATED,
                AggregateVersion.initial());
        artifact.recordEvent("voice.audio_artifact.created", tenantId, id, artifact.version, context,
                Map.of("sessionId", sessionId.value(), "turnId", turnId.value(), "purpose", purpose.name()));
        return artifact;
    }

    public static AudioArtifact rehydrate(
            ResourceId id,
            TenantId tenantId,
            ResourceId sessionId,
            ResourceId turnId,
            AudioPurpose purpose,
            ResourceId consentRecordId,
            Instant createdAt,
            Instant expiresAt,
            AudioArtifactState state,
            String codec,
            Integer sampleRate,
            Integer channelCount,
            Long bytes,
            Long durationMillis,
            StorageObjectRef storageObjectRef,
            String contentHash,
            ResourceId providerInvocationId,
            Instant deleteQueuedAt,
            Instant deletedAt,
            String failureCode,
            AggregateVersion version
    ) {
        AudioArtifact artifact = new AudioArtifact(id, tenantId, sessionId, turnId, purpose,
                consentRecordId, createdAt, expiresAt, state, version);
        artifact.codec = codec;
        artifact.sampleRate = sampleRate;
        artifact.channelCount = channelCount;
        artifact.bytes = bytes;
        artifact.durationMillis = durationMillis;
        artifact.storageObjectRef = storageObjectRef;
        artifact.contentHash = contentHash;
        artifact.providerInvocationId = providerInvocationId;
        artifact.deleteQueuedAt = deleteQueuedAt;
        artifact.deletedAt = deletedAt;
        artifact.failureCode = failureCode;
        artifact.assertConsistent();
        return artifact;
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ResourceId sessionId() { return sessionId; }
    public ResourceId turnId() { return turnId; }
    public AudioPurpose purpose() { return purpose; }
    public ResourceId consentRecordId() { return consentRecordId; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public AudioArtifactState state() { return state; }
    public Optional<String> codec() { return Optional.ofNullable(codec); }
    public Optional<Integer> sampleRate() { return Optional.ofNullable(sampleRate); }
    public Optional<Integer> channelCount() { return Optional.ofNullable(channelCount); }
    public Optional<Long> bytes() { return Optional.ofNullable(bytes); }
    public Optional<Long> durationMillis() { return Optional.ofNullable(durationMillis); }
    public Optional<StorageObjectRef> storageObjectRef() { return Optional.ofNullable(storageObjectRef); }
    public Optional<String> contentHash() { return Optional.ofNullable(contentHash); }
    public Optional<ResourceId> providerInvocationId() { return Optional.ofNullable(providerInvocationId); }
    public Optional<Instant> deleteQueuedAt() { return Optional.ofNullable(deleteQueuedAt); }
    public Optional<Instant> deletedAt() { return Optional.ofNullable(deletedAt); }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public AggregateVersion version() { return version; }

    public void beginUpload(String codec, Integer sampleRate, Integer channelCount,
                            AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(purpose == AudioPurpose.ANSWER_TRANSCRIPTION,
                DomainErrorCode.POLICY_DENIED, "only answer audio can enter browser upload lifecycle");
        DomainPreconditions.require(state == AudioArtifactState.CREATED
                        || state == AudioArtifactState.UPLOAD_FAILED,
                DomainErrorCode.INVALID_STATE, "audio artifact cannot start upload from current state");
        this.codec = DomainPreconditions.requireText(codec, "audioCodec");
        if (sampleRate != null) {
            DomainPreconditions.require(sampleRate > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "audio sample rate must be positive");
        }
        if (channelCount != null) {
            DomainPreconditions.require(channelCount > 0 && channelCount <= 8,
                    DomainErrorCode.INVALID_ARGUMENT, "audio channel count is invalid");
        }
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        failureCode = null;
        state = AudioArtifactState.UPLOADING;
        bump("voice.audio_artifact.upload_started", context, Map.of("codec", this.codec));
    }

    public void markUploaded(StorageObjectRef storageObjectRef, long bytes, long durationMillis,
                             String contentHash, AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.UPLOADING);
        DomainPreconditions.require(bytes > 0 && durationMillis > 0, DomainErrorCode.INVALID_ARGUMENT,
                "uploaded audio size and duration must be positive");
        this.storageObjectRef = DomainPreconditions.requireNonNull(storageObjectRef, "storageObjectRef");
        this.bytes = bytes;
        this.durationMillis = durationMillis;
        this.contentHash = DomainPreconditions.requireText(contentHash, "audioContentHash");
        state = AudioArtifactState.UPLOADED;
        bump("voice.audio_artifact.uploaded", context,
                Map.of("bytes", Long.toString(bytes), "durationMillis", Long.toString(durationMillis)));
    }

    public void failUpload(String failureCode, AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.UPLOADING);
        this.failureCode = DomainPreconditions.requireText(failureCode, "audioFailureCode");
        state = AudioArtifactState.UPLOAD_FAILED;
        bump("voice.audio_artifact.upload_failed", context, Map.of("failureCode", this.failureCode));
    }

    public void beginTranscription(AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(purpose == AudioPurpose.ANSWER_TRANSCRIPTION,
                DomainErrorCode.POLICY_DENIED, "only answer audio can be transcribed");
        requireState(AudioArtifactState.UPLOADED);
        state = AudioArtifactState.TRANSCRIBING;
        bump("voice.audio_artifact.transcription_started", context, Map.of());
    }

    public void markTranscribed(ResourceId providerInvocationId,
                                AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.TRANSCRIBING);
        this.providerInvocationId = DomainPreconditions.requireNonNull(
                providerInvocationId, "providerInvocationId");
        state = AudioArtifactState.TRANSCRIBED;
        bump("voice.audio_artifact.transcribed", context,
                Map.of("providerInvocationId", providerInvocationId.value()));
    }

    public void failTranscription(String failureCode, ResourceId providerInvocationId,
                                  AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.TRANSCRIBING);
        this.failureCode = DomainPreconditions.requireText(failureCode, "audioFailureCode");
        this.providerInvocationId = DomainPreconditions.requireNonNull(
                providerInvocationId, "providerInvocationId");
        state = AudioArtifactState.TRANSCRIBE_FAILED;
        bump("voice.audio_artifact.transcription_failed", context,
                Map.of("failureCode", this.failureCode));
    }

    public void beginSynthesis(String codec, Integer sampleRate, Integer channelCount,
                               AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(purpose == AudioPurpose.TTS_PLAYBACK,
                DomainErrorCode.POLICY_DENIED, "only TTS audio can enter synthesis lifecycle");
        requireState(AudioArtifactState.CREATED);
        this.codec = DomainPreconditions.requireText(codec, "audioCodec");
        if (sampleRate != null) {
            DomainPreconditions.require(sampleRate > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "audio sample rate must be positive");
        }
        if (channelCount != null) {
            DomainPreconditions.require(channelCount > 0 && channelCount <= 8,
                    DomainErrorCode.INVALID_ARGUMENT, "audio channel count is invalid");
        }
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        failureCode = null;
        state = AudioArtifactState.SYNTHESIZING;
        bump("voice.audio_artifact.synthesis_started", context, Map.of("codec", this.codec));
    }

    public void markSynthesized(StorageObjectRef storageObjectRef, long bytes, long durationMillis,
                                String contentHash, ResourceId providerInvocationId,
                                AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.SYNTHESIZING);
        DomainPreconditions.require(bytes > 0 && durationMillis > 0, DomainErrorCode.INVALID_ARGUMENT,
                "synthesized audio size and duration must be positive");
        this.storageObjectRef = DomainPreconditions.requireNonNull(storageObjectRef, "storageObjectRef");
        this.bytes = bytes;
        this.durationMillis = durationMillis;
        this.contentHash = DomainPreconditions.requireText(contentHash, "audioContentHash");
        this.providerInvocationId = DomainPreconditions.requireNonNull(
                providerInvocationId, "providerInvocationId");
        state = AudioArtifactState.SYNTHESIZED;
        bump("voice.audio_artifact.synthesized", context,
                Map.of("bytes", Long.toString(bytes), "durationMillis", Long.toString(durationMillis),
                        "providerInvocationId", providerInvocationId.value()));
    }

    public void failSynthesis(String failureCode, ResourceId providerInvocationId,
                              AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.SYNTHESIZING);
        this.failureCode = DomainPreconditions.requireText(failureCode, "audioFailureCode");
        this.providerInvocationId = DomainPreconditions.requireNonNull(
                providerInvocationId, "providerInvocationId");
        state = AudioArtifactState.SYNTHESIS_FAILED;
        bump("voice.audio_artifact.synthesis_failed", context,
                Map.of("failureCode", this.failureCode));
    }

    public void queueDeletion(AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state != AudioArtifactState.DELETED
                        && state != AudioArtifactState.DELETE_QUEUED,
                DomainErrorCode.INVALID_STATE, "audio artifact is already deleting or deleted");
        state = AudioArtifactState.DELETE_QUEUED;
        deleteQueuedAt = context.occurredAt();
        failureCode = null;
        bump("voice.audio_artifact.delete_queued", context, Map.of());
    }

    public void markDeleted(AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state == AudioArtifactState.DELETE_QUEUED
                        || state == AudioArtifactState.DELETE_PARTIAL,
                DomainErrorCode.INVALID_STATE, "audio artifact is not queued for deletion");
        state = AudioArtifactState.DELETED;
        deletedAt = context.occurredAt();
        storageObjectRef = null;
        failureCode = null;
        bump("voice.audio_artifact.deleted", context, Map.of());
    }

    public void markDeletePartial(String failureCode,
                                  AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.DELETE_QUEUED);
        this.failureCode = DomainPreconditions.requireText(failureCode, "audioDeleteFailureCode");
        state = AudioArtifactState.DELETE_PARTIAL;
        bump("voice.audio_artifact.delete_partial", context, Map.of("failureCode", this.failureCode));
    }

    public void retryDeletion(AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        requireState(AudioArtifactState.DELETE_PARTIAL);
        failureCode = null;
        state = AudioArtifactState.DELETE_QUEUED;
        bump("voice.audio_artifact.delete_retried", context, Map.of());
    }

    public boolean deletionDueAt(Instant now) {
        DomainPreconditions.requireNonNull(now, "now");
        return state != AudioArtifactState.DELETED && !now.isBefore(expiresAt);
    }

    private void expected(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void requireState(AudioArtifactState expected) {
        DomainPreconditions.require(state == expected, DomainErrorCode.INVALID_STATE,
                "audio artifact is not in required state");
    }

    private void bump(String type, EventContext context, Map<String, String> attributes) {
        DomainPreconditions.requireNonNull(context, "eventContext");
        version = version.next();
        recordEvent(type, tenantId, id, version, context, attributes);
    }

    private void assertConsistent() {
        boolean synthesisState = state == AudioArtifactState.SYNTHESIZING
                || state == AudioArtifactState.SYNTHESIZED
                || state == AudioArtifactState.SYNTHESIS_FAILED;
        boolean answerProcessingState = state == AudioArtifactState.UPLOADING
                || state == AudioArtifactState.UPLOADED
                || state == AudioArtifactState.UPLOAD_FAILED
                || state == AudioArtifactState.TRANSCRIBING
                || state == AudioArtifactState.TRANSCRIBED
                || state == AudioArtifactState.TRANSCRIBE_FAILED;
        DomainPreconditions.require(!synthesisState || purpose == AudioPurpose.TTS_PLAYBACK,
                DomainErrorCode.INVALID_STATE, "synthesis state requires TTS artifact purpose");
        DomainPreconditions.require(!answerProcessingState || purpose == AudioPurpose.ANSWER_TRANSCRIPTION,
                DomainErrorCode.INVALID_STATE, "answer processing state requires transcription artifact purpose");
        if (synthesisState) {
            DomainPreconditions.require(codec != null, DomainErrorCode.INVALID_STATE,
                    "synthesis state requires codec");
        }
        boolean uploadedFactRequired = switch (state) {
            case UPLOADED, TRANSCRIBING, TRANSCRIBED, TRANSCRIBE_FAILED, SYNTHESIZED -> true;
            default -> false;
        };
        if (uploadedFactRequired && deletedAt == null) {
            DomainPreconditions.require(storageObjectRef != null && bytes != null && durationMillis != null
                            && contentHash != null && codec != null,
                    DomainErrorCode.INVALID_STATE, "audio upload facts are incomplete");
        }
        boolean anyUploadFact = storageObjectRef != null || bytes != null || durationMillis != null
                || contentHash != null;
        if (anyUploadFact && state != AudioArtifactState.DELETED) {
            DomainPreconditions.require(storageObjectRef != null && bytes != null && durationMillis != null
                            && contentHash != null && codec != null,
                    DomainErrorCode.INVALID_STATE, "audio upload facts must be complete when present");
        }
        if (bytes != null) {
            DomainPreconditions.require(bytes > 0, DomainErrorCode.INVALID_STATE,
                    "audio byte count must be positive");
        }
        if (durationMillis != null) {
            DomainPreconditions.require(durationMillis > 0, DomainErrorCode.INVALID_STATE,
                    "audio duration must be positive");
        }
        if (sampleRate != null) {
            DomainPreconditions.require(sampleRate > 0, DomainErrorCode.INVALID_STATE,
                    "audio sample rate must be positive");
        }
        if (channelCount != null) {
            DomainPreconditions.require(channelCount > 0 && channelCount <= 8,
                    DomainErrorCode.INVALID_STATE, "audio channel count is invalid");
        }
        DomainPreconditions.require((state == AudioArtifactState.DELETED) == (deletedAt != null),
                DomainErrorCode.INVALID_STATE, "audio deletedAt is inconsistent with state");
        boolean deletionState = state == AudioArtifactState.DELETE_QUEUED
                || state == AudioArtifactState.DELETE_PARTIAL || state == AudioArtifactState.DELETED;
        DomainPreconditions.require(deletionState == (deleteQueuedAt != null),
                DomainErrorCode.INVALID_STATE, "audio deleteQueuedAt is inconsistent with state");
        boolean failed = state == AudioArtifactState.UPLOAD_FAILED
                || state == AudioArtifactState.TRANSCRIBE_FAILED
                || state == AudioArtifactState.SYNTHESIS_FAILED
                || state == AudioArtifactState.DELETE_PARTIAL;
        DomainPreconditions.require(failed == (failureCode != null), DomainErrorCode.INVALID_STATE,
                "audio failure code is inconsistent with state");
        if (state == AudioArtifactState.TRANSCRIBED || state == AudioArtifactState.TRANSCRIBE_FAILED
                || state == AudioArtifactState.SYNTHESIZED || state == AudioArtifactState.SYNTHESIS_FAILED) {
            DomainPreconditions.requireNonNull(providerInvocationId, "providerInvocationId");
        }
    }
}
