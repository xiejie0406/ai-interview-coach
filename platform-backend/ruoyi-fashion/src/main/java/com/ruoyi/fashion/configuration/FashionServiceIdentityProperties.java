package com.ruoyi.fashion.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Java/Python 服务身份配置。密钥字段没有默认值，生产值只能由部署环境或秘密管理注入。
 */
@ConfigurationProperties(prefix = "fashion.service-identity")
public class FashionServiceIdentityProperties {

    private String localServiceId = "ruoyi-fashion";
    private String peerServiceId = "fashion-ai-runtime";
    private Duration maxClockSkew = Duration.ofMinutes(5);
    private Duration nonceTtl = Duration.ofMinutes(10);
    private String activeKeyId;
    private String activeKeyBase64;
    private String previousKeyId;
    private String previousKeyBase64;

    public String getLocalServiceId() {
        return localServiceId;
    }

    public void setLocalServiceId(String localServiceId) {
        this.localServiceId = localServiceId;
    }

    public String getPeerServiceId() {
        return peerServiceId;
    }

    public void setPeerServiceId(String peerServiceId) {
        this.peerServiceId = peerServiceId;
    }

    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }

    public void setMaxClockSkew(Duration maxClockSkew) {
        this.maxClockSkew = maxClockSkew;
    }

    public Duration getNonceTtl() {
        return nonceTtl;
    }

    public void setNonceTtl(Duration nonceTtl) {
        this.nonceTtl = nonceTtl;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getActiveKeyBase64() {
        return activeKeyBase64;
    }

    public void setActiveKeyBase64(String activeKeyBase64) {
        this.activeKeyBase64 = activeKeyBase64;
    }

    public String getPreviousKeyId() {
        return previousKeyId;
    }

    public void setPreviousKeyId(String previousKeyId) {
        this.previousKeyId = previousKeyId;
    }

    public String getPreviousKeyBase64() {
        return previousKeyBase64;
    }

    public void setPreviousKeyBase64(String previousKeyBase64) {
        this.previousKeyBase64 = previousKeyBase64;
    }
}
