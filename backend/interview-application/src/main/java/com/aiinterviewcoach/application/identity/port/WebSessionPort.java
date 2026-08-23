package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.application.identity.ResolvedPrincipal;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.PrincipalRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.Optional;

/** Cookie token 只在入站安全 adapter 与响应映射间短暂存在，不进入响应 JSON、日志或领域对象。 */
public interface WebSessionPort {

    IssuedSession issue(PrincipalRef principal, Instant issuedAt);

    Optional<ResolvedPrincipal> resolve(String sessionToken);

    IssuedSession rotate(String currentSessionToken, Instant rotatedAt);

    void revoke(TenantId tenantId, ResourceId sessionId, Instant revokedAt);

    record IssuedSession(
            ResourceId sessionId,
            String opaqueSessionToken,
            String csrfToken,
            Instant expiresAt,
            Instant idleExpiresAt
    ) {
        public IssuedSession {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            opaqueSessionToken = DomainPreconditions.requireText(opaqueSessionToken, "opaqueSessionToken");
            csrfToken = DomainPreconditions.requireText(csrfToken, "csrfToken");
            DomainPreconditions.requireNonNull(expiresAt, "sessionExpiresAt");
            DomainPreconditions.requireNonNull(idleExpiresAt, "sessionIdleExpiresAt");
        }

        @Override
        public String toString() {
            return "IssuedSession[sessionId=" + sessionId
                    + ", opaqueSessionToken=<redacted>, csrfToken=<redacted>, expiresAt=" + expiresAt
                    + ", idleExpiresAt=" + idleExpiresAt + "]";
        }
    }
}
