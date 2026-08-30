package com.ruoyi.interview.domain.voice;

public enum AudioArtifactState {
    CREATED,
    UPLOADING,
    UPLOADED,
    TRANSCRIBING,
    TRANSCRIBED,
    SYNTHESIZING,
    SYNTHESIZED,
    UPLOAD_FAILED,
    TRANSCRIBE_FAILED,
    SYNTHESIS_FAILED,
    DELETE_QUEUED,
    DELETE_PARTIAL,
    DELETED
}
