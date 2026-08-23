package com.aiinterviewcoach.application.voice.internal;

import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.voice.GetAudioArtifactStatus;
import com.aiinterviewcoach.application.voice.port.InterviewVoiceAccessPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;

import java.util.Map;

public final class DefaultGetAudioArtifactStatus implements GetAudioArtifactStatus {

    private final VoiceRepository repository;
    private final InterviewVoiceAccessPort interviewAccess;

    public DefaultGetAudioArtifactStatus(VoiceRepository repository,
                                         InterviewVoiceAccessPort interviewAccess) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.interviewAccess = java.util.Objects.requireNonNull(interviewAccess);
    }

    @Override
    public View handle(Query query) {
        var principal = query.context().principal();
        var artifact = repository.findArtifact(principal.tenantId(), query.artifactId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "audio artifact was not found", false, Map.of()));
        var access = interviewAccess.inspect(principal.tenantId(), principal.userId(),
                artifact.sessionId(), artifact.turnId());
        if (!access.allowed()) {
            throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                    "audio artifact was not found", false, Map.of());
        }
        var deleteStatus = switch (artifact.state()) {
            case DELETE_QUEUED -> java.util.Optional.of("QUEUED");
            case DELETE_PARTIAL -> java.util.Optional.of("PARTIAL_FAILED");
            case DELETED -> java.util.Optional.of("COMPLETED");
            default -> java.util.Optional.<String>empty();
        };
        return new View(artifact.id(), artifact.state(), artifact.purpose(), artifact.expiresAt(),
                artifact.codec(), artifact.bytes(), artifact.durationMillis(), artifact.failureCode(),
                deleteStatus, artifact.version());
    }
}
