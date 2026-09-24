package com.ruoyi.fashion.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fashion.customer-contact")
public class FashionCustomerContactCipherProperties {
    private String activeKeyId;
    private String activeKeyBase64;
    private String previousKeyId;
    private String previousKeyBase64;

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId; }
    public String getActiveKeyBase64() { return activeKeyBase64; }
    public void setActiveKeyBase64(String activeKeyBase64) { this.activeKeyBase64 = activeKeyBase64; }
    public String getPreviousKeyId() { return previousKeyId; }
    public void setPreviousKeyId(String previousKeyId) { this.previousKeyId = previousKeyId; }
    public String getPreviousKeyBase64() { return previousKeyBase64; }
    public void setPreviousKeyBase64(String previousKeyBase64) { this.previousKeyBase64 = previousKeyBase64; }
}
