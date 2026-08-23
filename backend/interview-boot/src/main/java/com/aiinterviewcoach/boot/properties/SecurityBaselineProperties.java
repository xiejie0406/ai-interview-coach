package com.aiinterviewcoach.boot.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

/** Phase 01 安全红线；放宽任一值属于设计变更，不能作为部署便利开关。 */
@Validated
@ConfigurationProperties(prefix = "interview.security")
public class SecurityBaselineProperties {
    @AssertTrue(message = "session and CSRF cookies must remain secure")
    private boolean secureCookies = true;

    @AssertTrue(message = "cross-origin browser access is not approved in the foundation phase")
    private boolean sameOriginOnly = true;

    @NotBlank
    private String sameSite = "Strict";

    public boolean isSecureCookies() {
        return secureCookies;
    }

    public void setSecureCookies(boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    public boolean isSameOriginOnly() {
        return sameOriginOnly;
    }

    public void setSameOriginOnly(boolean sameOriginOnly) {
        this.sameOriginOnly = sameOriginOnly;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    @AssertTrue(message = "SameSite=None is not approved in the same-origin security baseline")
    public boolean isApprovedSameSitePolicy() {
        if (sameSite == null) {
            return false;
        }
        String normalized = sameSite.toLowerCase(Locale.ROOT);
        return normalized.equals("strict") || normalized.equals("lax");
    }
}
