package com.ruoyi.interview.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Voice 本地运行适配器配置；Secret 只允许由外部环境注入。 */
@ConfigurationProperties(prefix = "interview.voice-runtime")
public class VoiceRuntimeProperties {
    private String localStorageRoot = "";
    private String sensitiveEnvelopeKeyId = "";
    private String sensitiveEnvelopeKeyBase64 = "";

    public String getLocalStorageRoot() {
        return localStorageRoot;
    }

    public void setLocalStorageRoot(String localStorageRoot) {
        this.localStorageRoot = localStorageRoot;
    }

    public String getSensitiveEnvelopeKeyId() {
        return sensitiveEnvelopeKeyId;
    }

    public void setSensitiveEnvelopeKeyId(String sensitiveEnvelopeKeyId) {
        this.sensitiveEnvelopeKeyId = sensitiveEnvelopeKeyId;
    }

    public String getSensitiveEnvelopeKeyBase64() {
        return sensitiveEnvelopeKeyBase64;
    }

    public void setSensitiveEnvelopeKeyBase64(String sensitiveEnvelopeKeyBase64) {
        this.sensitiveEnvelopeKeyBase64 = sensitiveEnvelopeKeyBase64;
    }
}
