package com.aiinterviewcoach.adapters.inbound.rest.identity;

import java.util.Locale;

/** Boot 从同一安全配置源装配；构造器禁止产生非 Secure 或 SameSite=None 的认证 Cookie。 */
public record SessionCookiePolicy(boolean secure, String sameSite, String path) {

    public SessionCookiePolicy {
        if (!secure) {
            throw new IllegalArgumentException("AIC_SESSION cookie must remain Secure");
        }
        if (sameSite == null) {
            throw new IllegalArgumentException("AIC_SESSION SameSite policy is required");
        }
        sameSite = switch (sameSite.strip().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            default -> throw new IllegalArgumentException("AIC_SESSION SameSite must be Strict or Lax");
        };
        if (!"/".equals(path)) {
            throw new IllegalArgumentException("AIC_SESSION cookie path must remain /");
        }
    }
}
