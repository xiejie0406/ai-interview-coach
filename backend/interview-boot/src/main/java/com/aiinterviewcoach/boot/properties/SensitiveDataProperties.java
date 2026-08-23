package com.aiinterviewcoach.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 外部 secret source 注入的敏感字段 keyring；属性对象和异常都不得输出 key material。 */
@ConfigurationProperties(prefix = "interview.security.sensitive-data")
public class SensitiveDataProperties {

    private String currentKeyId;
    private String currentKeyBase64;
    private Map<String, String> decryptionKeys = new LinkedHashMap<>();

    public String getCurrentKeyId() {
        return currentKeyId;
    }

    public void setCurrentKeyId(String currentKeyId) {
        this.currentKeyId = currentKeyId;
    }

    public String getCurrentKeyBase64() {
        return currentKeyBase64;
    }

    public void setCurrentKeyBase64(String currentKeyBase64) {
        this.currentKeyBase64 = currentKeyBase64;
    }

    public Map<String, String> getDecryptionKeys() {
        return Map.copyOf(decryptionKeys == null ? Map.of() : decryptionKeys);
    }

    public void setDecryptionKeys(Map<String, String> decryptionKeys) {
        this.decryptionKeys = new LinkedHashMap<>(decryptionKeys == null ? Map.of() : decryptionKeys);
    }

    @Override
    public String toString() {
        return "SensitiveDataProperties[currentKeyId=" + currentKeyId + ", keyMaterial=<redacted>]";
    }
}
