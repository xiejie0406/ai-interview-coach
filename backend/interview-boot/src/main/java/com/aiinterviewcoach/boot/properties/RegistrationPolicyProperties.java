package com.aiinterviewcoach.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/** 注册时服务端权威政策引用；不包含政策正文、URL 或供应商配置。 */
@ConfigurationProperties(prefix = "interview.registration-policy")
public class RegistrationPolicyProperties {

    private Set<String> supportedLocales = new LinkedHashSet<>();
    private VersionReference serviceTerms = new VersionReference();
    private VersionReference privacyNotice = new VersionReference();

    public Set<String> getSupportedLocales() {
        return Set.copyOf(supportedLocales == null ? Set.of() : supportedLocales);
    }

    public void setSupportedLocales(Set<String> supportedLocales) {
        this.supportedLocales = new LinkedHashSet<>(supportedLocales == null ? Set.of() : supportedLocales);
    }

    public VersionReference getServiceTerms() {
        return serviceTerms;
    }

    public void setServiceTerms(VersionReference serviceTerms) {
        this.serviceTerms = serviceTerms == null ? new VersionReference() : serviceTerms;
    }

    public VersionReference getPrivacyNotice() {
        return privacyNotice;
    }

    public void setPrivacyNotice(VersionReference privacyNotice) {
        this.privacyNotice = privacyNotice == null ? new VersionReference() : privacyNotice;
    }

    public static class VersionReference {
        private String id;
        private Integer versionNo;
        private String contentHash;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Integer getVersionNo() {
            return versionNo;
        }

        public void setVersionNo(Integer versionNo) {
            this.versionNo = versionNo;
        }

        public String getContentHash() {
            return contentHash;
        }

        public void setContentHash(String contentHash) {
            this.contentHash = contentHash;
        }
    }
}
