package com.aiinterviewcoach.domain.voice;

public enum VoiceTurnState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    CONFIRMING,
    THINKING,
    SPEAKING,
    DEGRADED,
    CANCELLED
}
