package com.ruoyi.interview.application.voice.port;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.voice.AudioArtifact;
import com.ruoyi.interview.domain.voice.Transcript;
import com.ruoyi.interview.domain.voice.VoiceTurnExecution;

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
