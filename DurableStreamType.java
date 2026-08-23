package com.aiinterviewcoach.domain.platform;

/** 可由 PostgreSQL 权威事实恢复的下行事件流；不包含临时 delta、typing 或 progress hint。 */
public enum DurableStreamType {
    INTERVIEW,
    EVALUATION
}
