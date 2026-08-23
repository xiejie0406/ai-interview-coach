package com.aiinterviewcoach.domain.voice;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 转写版本与用户确认事实的唯一 owner。 */
public final class Transcript extends AggregateRoot {

    private final ResourceId id;
    private final TenantId tenantId;
    private final ResourceId sessionId;
    private final ResourceId turnId;
    private final ResourceId audioArtifactId;
    private final List<TranscriptVersion> versions;
    private TranscriptState state;
    private ResourceId confirmedVersionId;
    private UserId confirmedBy;
    private Instant confirmedAt;
    private AggregateVersion version;

    private Transcript(ResourceId id, TenantId tenantId, ResourceId sessionId, ResourceId turnId,
                       ResourceId audioArtifactId, TranscriptState state,
                       List<TranscriptVersion> versions, AggregateVersion version) {
        this.id = DomainPreconditions.requireNonNull(id, "transcriptId");
        this.tenantId = DomainPreconditions.requireNonNull(tenantId, "tenantId");
        this.sessionId = DomainPreconditions.requireNonNull(sessionId, "sessionId");
        this.turnId = DomainPreconditions.requireNonNull(turnId, "turnId");
        this.audioArtifactId = DomainPreconditions.requireNonNull(audioArtifactId, "audioArtifactId");
        this.state = DomainPreconditions.requireNonNull(state, "transcriptState");
        this.versions = new ArrayList<>(versions == null ? List.of() : versions);
        this.version = DomainPreconditions.requireNonNull(version, "transcriptAggregateVersion");
        assertVersions();
    }

    public static Transcript open(ResourceId id, AudioArtifact artifact, EventContext context) {
        DomainPreconditions.requireNonNull(artifact, "audioArtifact");
        DomainPreconditions.require(artifact.purpose() == AudioPurpose.ANSWER_TRANSCRIPTION,
                DomainErrorCode.POLICY_DENIED, "transcript requires answer audio");
        DomainPreconditions.require(artifact.state() == AudioArtifactState.TRANSCRIBED,
                DomainErrorCode.INVALID_STATE, "audio artifact must be transcribed first");
        Transcript transcript = new Transcript(id, artifact.tenantId(), artifact.sessionId(), artifact.turnId(),
                artifact.id(), TranscriptState.OPEN, List.of(), AggregateVersion.initial());
        transcript.recordEvent("voice.transcript.opened", transcript.tenantId, id, transcript.version, context,
                Map.of("audioArtifactId", artifact.id().value()));
        return transcript;
    }

    public static Transcript rehydrate(ResourceId id, TenantId tenantId, ResourceId sessionId,
                                       ResourceId turnId, ResourceId audioArtifactId, TranscriptState state,
                                       List<TranscriptVersion> versions, ResourceId confirmedVersionId,
                                       UserId confirmedBy, Instant confirmedAt, AggregateVersion version) {
        Transcript transcript = new Transcript(id, tenantId, sessionId, turnId, audioArtifactId,
                state, versions, version);
        transcript.confirmedVersionId = confirmedVersionId;
        transcript.confirmedBy = confirmedBy;
        transcript.confirmedAt = confirmedAt;
        transcript.assertConsistent();
        return transcript;
    }

    public ResourceId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ResourceId sessionId() { return sessionId; }
    public ResourceId turnId() { return turnId; }
    public ResourceId audioArtifactId() { return audioArtifactId; }
    public TranscriptState state() { return state; }
    public List<TranscriptVersion> versions() { return List.copyOf(versions); }
    public Optional<TranscriptVersion> latestVersion() {
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(versions.size() - 1));
    }
    public Optional<ResourceId> confirmedVersionId() { return Optional.ofNullable(confirmedVersionId); }
    public Optional<UserId> confirmedBy() { return Optional.ofNullable(confirmedBy); }
    public Optional<Instant> confirmedAt() { return Optional.ofNullable(confirmedAt); }
    public AggregateVersion version() { return version; }

    public void appendAsrFinal(TranscriptVersion transcriptVersion,
                               AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state == TranscriptState.OPEN && versions.isEmpty(),
                DomainErrorCode.INVALID_STATE, "ASR final can only initialize an open transcript");
        requireOwnedVersion(transcriptVersion, 1, TranscriptSource.ASR);
        versions.add(transcriptVersion);
        state = TranscriptState.ASR_FINAL;
        bump("voice.transcript.asr_final", context,
                Map.of("transcriptVersionId", transcriptVersion.id().value()));
    }

    public void appendCorrection(TranscriptVersion correction,
                                 AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state == TranscriptState.ASR_FINAL, DomainErrorCode.INVALID_STATE,
                "only an unconfirmed ASR transcript can be corrected");
        requireOwnedVersion(correction, versions.size() + 1, TranscriptSource.USER_CORRECTION);
        ResourceId expectedSuperseded = latestVersion().orElseThrow().id();
        DomainPreconditions.require(correction.supersedesId().filter(expectedSuperseded::equals).isPresent(),
                DomainErrorCode.VERSION_CONFLICT, "correction must supersede latest transcript version");
        versions.add(correction);
        bump("voice.transcript.corrected", context,
                Map.of("transcriptVersionId", correction.id().value()));
    }

    public void confirm(ResourceId transcriptVersionId, UserId userId,
                        AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state == TranscriptState.ASR_FINAL, DomainErrorCode.INVALID_STATE,
                "only an ASR final transcript can be confirmed");
        TranscriptVersion confirmed = versions.stream()
                .filter(candidate -> candidate.id().equals(transcriptVersionId))
                .findFirst()
                .orElseThrow(() -> new com.aiinterviewcoach.domain.platform.DomainException(
                        DomainErrorCode.VERSION_CONFLICT, "transcript version is not part of this transcript"));
        DomainPreconditions.require(confirmed.versionNo() == versions.size(), DomainErrorCode.VERSION_CONFLICT,
                "only latest transcript version can be confirmed");
        confirmedVersionId = transcriptVersionId;
        confirmedBy = DomainPreconditions.requireNonNull(userId, "confirmedBy");
        confirmedAt = context.occurredAt();
        state = TranscriptState.CONFIRMED;
        bump("voice.transcript.confirmed", context,
                Map.of("transcriptVersionId", transcriptVersionId.value(), "confirmedBy", userId.value()));
    }

    public void cancel(AggregateVersion expectedVersion, EventContext context) {
        expected(expectedVersion);
        DomainPreconditions.require(state == TranscriptState.OPEN || state == TranscriptState.ASR_FINAL,
                DomainErrorCode.INVALID_STATE, "confirmed or cancelled transcript cannot be cancelled");
        state = TranscriptState.CANCELLED;
        bump("voice.transcript.cancelled", context, Map.of());
    }

    private void requireOwnedVersion(TranscriptVersion candidate, int expectedVersionNo,
                                     TranscriptSource expectedSource) {
        DomainPreconditions.requireNonNull(candidate, "transcriptVersion");
        DomainPreconditions.require(candidate.tenantId().equals(tenantId)
                        && candidate.transcriptId().equals(id),
                DomainErrorCode.TENANT_MISMATCH, "transcript version belongs to another transcript");
        DomainPreconditions.require(candidate.versionNo() == expectedVersionNo,
                DomainErrorCode.VERSION_CONFLICT, "transcript version number is not contiguous");
        DomainPreconditions.require(candidate.source() == expectedSource, DomainErrorCode.INVALID_ARGUMENT,
                "transcript version source is invalid for this operation");
        DomainPreconditions.require(versions.stream().noneMatch(existing -> existing.id().equals(candidate.id())),
                DomainErrorCode.VERSION_CONFLICT, "transcript version id has already been used");
    }

    private void expected(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private void bump(String type, EventContext context, Map<String, String> attributes) {
        version = version.next();
        recordEvent(type, tenantId, id, version, context, attributes);
    }

    private void assertVersions() {
        DomainPreconditions.require(new HashSet<>(versions.stream().map(TranscriptVersion::id).toList()).size()
                        == versions.size(), DomainErrorCode.INVALID_STATE,
                "transcript contains duplicate version ids");
        for (int index = 0; index < versions.size(); index++) {
            TranscriptVersion candidate = versions.get(index);
            DomainPreconditions.require(candidate.tenantId().equals(tenantId)
                            && candidate.transcriptId().equals(id) && candidate.versionNo() == index + 1,
                    DomainErrorCode.INVALID_STATE, "transcript version chain is invalid");
        }
    }

    private void assertConsistent() {
        assertVersions();
        if (state == TranscriptState.OPEN) {
            DomainPreconditions.require(versions.isEmpty(), DomainErrorCode.INVALID_STATE,
                    "open transcript must not contain versions");
        }
        boolean confirmed = state == TranscriptState.CONFIRMED;
        DomainPreconditions.require(confirmed == (confirmedVersionId != null
                        && confirmedBy != null && confirmedAt != null),
                DomainErrorCode.INVALID_STATE, "transcript confirmation fields are inconsistent");
        if (confirmed) {
            DomainPreconditions.require(latestVersion().map(TranscriptVersion::id)
                            .filter(confirmedVersionId::equals).isPresent(),
                    DomainErrorCode.INVALID_STATE, "confirmed transcript must reference latest version");
        }
        if (state == TranscriptState.ASR_FINAL || state == TranscriptState.CONFIRMED) {
            DomainPreconditions.require(!versions.isEmpty() && versions.get(0).source() == TranscriptSource.ASR,
                    DomainErrorCode.INVALID_STATE, "transcript requires an ASR source version");
        }
    }
}
