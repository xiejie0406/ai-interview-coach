package com.aiinterviewcoach.boot.properties;

import jakarta.validation.constraints.AssertFalse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 默认关闭的能力锁；Voice MVP 仅允许环境显式开启已实现的 WS 与本地对象写入。 */
@Validated
@ConfigurationProperties(prefix = "interview.foundation-safety")
public class FoundationSafetyProperties {
    /** Local/UAT business REST entry points; disabled by default and enabled explicitly per environment. */
    private boolean businessRestEndpointsEnabled;

    /** Identity is an independently gated foundation slice; other business REST remains disabled by default. */
    private boolean identityRestEndpointsEnabled;

    public boolean isIdentityRestEndpointsEnabled() {
        return identityRestEndpointsEnabled;
    }

    public void setIdentityRestEndpointsEnabled(boolean identityRestEndpointsEnabled) {
        this.identityRestEndpointsEnabled = identityRestEndpointsEnabled;
    }

    @AssertFalse(message = "SSE business endpoints are not approved in Phase 01")
    private boolean sseEndpointsEnabled;

    private boolean webSocketEndpointsEnabled;

    @AssertFalse(message = "external provider calls are not approved in Phase 01")
    private boolean externalProviderCallsEnabled;

    private boolean objectStorageWritesEnabled;

    @AssertFalse(message = "background job execution is not approved in Phase 01")
    private boolean backgroundJobsEnabled;

    public boolean isBusinessRestEndpointsEnabled() {
        return businessRestEndpointsEnabled;
    }

    public void setBusinessRestEndpointsEnabled(boolean businessRestEndpointsEnabled) {
        this.businessRestEndpointsEnabled = businessRestEndpointsEnabled;
    }

    public boolean isSseEndpointsEnabled() {
        return sseEndpointsEnabled;
    }

    public void setSseEndpointsEnabled(boolean sseEndpointsEnabled) {
        this.sseEndpointsEnabled = sseEndpointsEnabled;
    }

    public boolean isWebSocketEndpointsEnabled() {
        return webSocketEndpointsEnabled;
    }

    public void setWebSocketEndpointsEnabled(boolean webSocketEndpointsEnabled) {
        this.webSocketEndpointsEnabled = webSocketEndpointsEnabled;
    }

    public boolean isExternalProviderCallsEnabled() {
        return externalProviderCallsEnabled;
    }

    public void setExternalProviderCallsEnabled(boolean externalProviderCallsEnabled) {
        this.externalProviderCallsEnabled = externalProviderCallsEnabled;
    }

    public boolean isObjectStorageWritesEnabled() {
        return objectStorageWritesEnabled;
    }

    public void setObjectStorageWritesEnabled(boolean objectStorageWritesEnabled) {
        this.objectStorageWritesEnabled = objectStorageWritesEnabled;
    }

    public boolean isBackgroundJobsEnabled() {
        return backgroundJobsEnabled;
    }

    public void setBackgroundJobsEnabled(boolean backgroundJobsEnabled) {
        this.backgroundJobsEnabled = backgroundJobsEnabled;
    }
}
