package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.ResolvePrincipal;
import com.aiinterviewcoach.application.identity.ResolvedPrincipal;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

import java.util.Map;
import java.util.Set;

/** 每个 HTTP/SSE/WebSocket 请求重新解析并重验服务端 Session。 */
public final class DefaultResolvePrincipal implements ResolvePrincipal {

    private final WebSessionPort webSession;
    private final ActivePrincipalGuard activePrincipal;
    private final IdentityRepository identityRepository;

    public DefaultResolvePrincipal(WebSessionPort webSession,
                                   ActivePrincipalGuard activePrincipal,
                                   IdentityRepository identityRepository) {
        this.webSession = java.util.Objects.requireNonNull(webSession);
        this.activePrincipal = java.util.Objects.requireNonNull(activePrincipal);
        this.identityRepository = java.util.Objects.requireNonNull(identityRepository);
    }

    @Override
    public ResolvedPrincipal handle(Query query) {
        ResolvedPrincipal issued = webSession.resolve(query.sessionToken()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.AUTH_REQUIRED,
                        "session is missing or expired", false, Map.of()));
        PrincipalRef principal = activePrincipal.requireActive(issued.principalRef());
        Membership membership = identityRepository.findMembership(principal.tenantId(), principal.userId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.AUTH_REQUIRED,
                        "session principal is no longer active", false, Map.of()));
        return new ResolvedPrincipal(principal, issued.sessionId(), Set.of(membership.role()), issued.expiresAt());
    }
}
