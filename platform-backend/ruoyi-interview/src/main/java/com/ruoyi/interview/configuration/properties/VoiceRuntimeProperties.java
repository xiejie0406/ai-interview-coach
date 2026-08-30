package com.ruoyi.interview.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Voice 本地运行适配器配置；Secret 只允许由外部环境注入。 */
@ConfigurationProperties(prefix = "interview.voice-runtime")
public class VoiceRuntimeProperties {
    /** ticket 存储后端：memory 仅用于单实例开发，redis 用于生产多实例。 */
    private String ticketStore = "memory";
    private String ticketRedisKeyPrefix = "";
    /**
     * WebSocket Origin 白名单。生产环境必须通过外部配置注入真实 portal origin，
     * 不允许使用裸通配符；本地默认值仅用于开发。
     */
    private List<String> allowedOriginPatterns = List.of("http://localhost:*", "http://127.0.0.1:*");
    private String localStorageRoot = "";
    private String sensitiveEnvelopeKeyId = "";
    private String sensitiveEnvelopeKeyBase64 = "";

    public String getTicketStore() {
        return ticketStore;
    }

    public void setTicketStore(String ticketStore) {
        this.ticketStore = ticketStore;
    }

    public String getTicketRedisKeyPrefix() {
        return ticketRedisKeyPrefix;
    }

    public void setTicketRedisKeyPrefix(String ticketRedisKeyPrefix) {
        this.ticketRedisKeyPrefix = ticketRedisKeyPrefix;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

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
