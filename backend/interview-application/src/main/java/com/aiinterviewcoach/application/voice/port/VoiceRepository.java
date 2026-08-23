package com.aiinterviewcoach.application.voice.port;

import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.voice.AudioArtifact;
import com.aiinterviewcoach.domain.voice.Transcript;
import com.aiinterviewcoach.domain.voice.VoiceTurnExecution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoiceRepository {

    Optional<AudioArtifact> findArtifact(TenantId tenantId, ResourceId artifactId);

    Optional<Transcript> findTranscript(TenantId tenantId, ResourceId transcriptId);

    Optional<VoiceTurnExecution> findExecution(TenantId tenantId, ResourceId sessionId, ResourceId turnId);

    List<AudioArtifact> findDeletionDue(Instant now, int limit);

    void saveArtifact(AudioArtifact artifact);

    void saveTranscript(Transcript transcript);

    void saveExecution(VoiceTurnExecution execution);
}
