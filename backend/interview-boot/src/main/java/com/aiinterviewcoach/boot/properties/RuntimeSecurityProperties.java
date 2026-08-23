package com.aiinterviewcoach.boot.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Runtime secret and retention inputs for production adapters.
 *
 * <p>Values must come from an operator-managed secret source. Empty defaults are intentionally invalid so local source
 * code cannot silently fall back to plaintext, fixed keys or mock sessions.</p>
 */
@Validated
@ConfigurationProperties(prefix = "interview.runtime")
public class RuntimeSecurityProperties {

    @NotBlank
    private String persistenceKeyId;

    @NotBlank
    private String persistenceKeyBase64;

    @NotBlank
    private String identityIdentifierHmacKeyBase64;

    @NotBlank
    private String webSessionTokenHmacKeyBase64;

    private Duration sessionAbsoluteTtl = Duration.ofHours(24);
    private Duration sessionIdleTtl = Duration.ofMinutes(30);
    private Duration idempotencyTtl = Duration.ofHours(24);
    private Duration interviewStreamRetention = Duration.ofDays(7);
    private Duration evaluationStreamRetention = Duration.ofDays(7);

    public String getPersistenceKeyId() {
        return persistenceKeyId;
    }

    public void setPersistenceKeyId(String persistenceKeyId) {
        this.persistenceKeyId = persistenceKeyId;
    }

    public String getPersistenceKeyBase64() {
        return persistenceKeyBase64;
    }

    public void setPersistenceKeyBase64(String persistenceKeyBase64) {
        this.persistenceKeyBase64 = persistenceKeyBase64;
    }

    public String getIdentityIdentifierHmacKeyBase64() {
        return identityIdentifierHmacKeyBase64;
    }

    public void setIdentityIdentifierHmacKeyBase64(String identityIdentifierHmacKeyBase64) {
        this.identityIdentifierHmacKeyBase64 = identityIdentifierHmacKeyBase64;
    }

    public String getWebSessionTokenHmacKeyBase64() {
        return webSessionTokenHmacKeyBase64;
    }

    public void setWebSessionTokenHmacKeyBase64(String webSessionTokenHmacKeyBase64) {
        this.webSessionTokenHmacKeyBase64 = webSessionTokenHmacKeyBase64;
    }

    public Duration getSessionAbsoluteTtl() {
        return sessionAbsoluteTtl;
    }

    public void setSessionAbsoluteTtl(Duration sessionAbsoluteTtl) {
        this.sessionAbsoluteTtl = sessionAbsoluteTtl;
    }

    public Duration getSessionIdleTtl() {
        return sessionIdleTtl;
    }

    public void setSessionIdleTtl(Duration sessionIdleTtl) {
        this.sessionIdleTtl = sessionIdleTtl;
    }

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

    @AssertTrue(message = "session TTLs and stream retentions must be positive and ordered")
    public boolean isValidDurations() {
        return positive(sessionAbsoluteTtl)
                && positive(sessionIdleTtl)
                && sessionIdleTtl.compareTo(sessionAbsoluteTtl) <= 0
                && positive(idempotencyTtl)
                && positive(interviewStreamRetention)
                && positive(evaluationStreamRetention);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
