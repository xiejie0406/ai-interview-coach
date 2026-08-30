package com.ruoyi.interview.domain.voice;

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
