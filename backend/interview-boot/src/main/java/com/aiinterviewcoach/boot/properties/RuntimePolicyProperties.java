package com.aiinterviewcoach.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 需经架构决策确认的运行时生命周期；不在源码中猜写默认时长。 */
@ConfigurationProperties(prefix = "interview.runtime-policy")
public class RuntimePolicyProperties {

    private Duration idempotencyTtl;
    private Duration interviewStreamRetention;
    private Duration evaluationStreamRetention;
    private Duration voiceInputRetention;
    private Duration voiceSessionTicketTtl;

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public Duration getInterviewStreamRetention() {
        return interviewStreamRetention;
    }

    public void setInterviewStreamRetention(Duration interviewStreamRetention) {
        this.interviewStreamRetention = interviewStreamRetention;
    }

    public Duration getEvaluationStreamRetention() {
        return evaluationStreamRetention;
    }

    public void setEvaluationStreamRetention(Duration evaluationStreamRetention) {
        this.evaluationStreamRetention = evaluationStreamRetention;
    }

    public Duration getVoiceInputRetention() {
        return voiceInputRetention;
    }

    public void setVoiceInputRetention(Duration voiceInputRetention) {
        this.voiceInputRetention = voiceInputRetention;
    }

    public Duration getVoiceSessionTicketTtl() {
        return voiceSessionTicketTtl;
    }

    public void setVoiceSessionTicketTtl(Duration voiceSessionTicketTtl) {
        this.voiceSessionTicketTtl = voiceSessionTicketTtl;
    }
}
