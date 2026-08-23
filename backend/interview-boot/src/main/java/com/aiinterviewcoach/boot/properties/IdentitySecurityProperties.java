package com.aiinterviewcoach.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Identity secret 与 Session 生命周期配置；所有值在启用业务入口时由 Boot 做 fail-closed 校验。 */
@ConfigurationProperties(prefix = "interview.security.identity")
public class IdentitySecurityProperties {

    private String identifierHmacKeyBase64;
    private String sessionHmacKeyBase64;
    private Duration sessionAbsoluteTtl;
    private Duration sessionIdleTtl;

    public String getIdentifierHmacKeyBase64() {
        return identifierHmacKeyBase64;
    }

    public void setIdentifierHmacKeyBase64(String identifierHmacKeyBase64) {
        this.identifierHmacKeyBase64 = identifierHmacKeyBase64;
    }

    public String getSessionHmacKeyBase64() {
        return sessionHmacKeyBase64;
    }

    public void setSessionHmacKeyBase64(String sessionHmacKeyBase64) {
        this.sessionHmacKeyBase64 = sessionHmacKeyBase64;
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

    @Override
    public String toString() {
        return "IdentitySecurityProperties[keyMaterial=<redacted>, sessionTtlConfigured="
                + (sessionAbsoluteTtl != null && sessionIdleTtl != null) + "]";
    }
}
