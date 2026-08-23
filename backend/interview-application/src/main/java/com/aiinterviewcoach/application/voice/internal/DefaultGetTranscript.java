package com.aiinterviewcoach.application.voice.internal;

import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.voice.GetTranscript;
import com.aiinterviewcoach.application.voice.port.InterviewVoiceAccessPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;

import java.util.Map;

public final class DefaultGetTranscript implements GetTranscript {

    private final VoiceRepository repository;
    private final InterviewVoiceAccessPort interviewAccess;

    public DefaultGetTranscript(VoiceRepository repository,
                                InterviewVoiceAccessPort interviewAccess) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.interviewAccess = java.util.Objects.requireNonNull(interviewAccess);
    }

    @Override
    public View handle(Query query) {
        var principal = query.context().principal();
        var transcript = repository.findTranscript(principal.tenantId(), query.transcriptId())
                .orElseThrow(() -> notFound());
        var access = interviewAccess.inspect(principal.tenantId(), principal.userId(),
                transcript.sessionId(), transcript.turnId());
        if (!access.allowed()) {
            throw notFound();
        }
        var latest = transcript.latestVersion().map(version -> new VersionView(
                version.id(), version.versionNo(), version.source(), version.text(),
                version.language(), version.offsetUnit(), version.lowConfidenceSpans(),
                version.createdAt()));
        return new View(transcript.id(), transcript.state(), transcript.sessionId(),
                transcript.turnId(), transcript.audioArtifactId(), latest,
                transcript.confirmedVersionId(), transcript.version());
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "transcript was not found", false, Map.of());
    }
}
