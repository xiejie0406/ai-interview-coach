package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.AuthenticateUser;
import com.aiinterviewcoach.application.identity.ResolvedPrincipal;
import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.identity.IdentityPolicy;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

import java.util.Map;

/** 认证成功后只签发服务端 Cookie Session，不把 token 放进业务结果。 */
public final class DefaultAuthenticateUser implements AuthenticateUser {

    private final IdentityChannelPort identityChannel;
    private final IdentityRepository identityRepository;
    private final WebSessionPort webSession;

    public DefaultAuthenticateUser(IdentityChannelPort identityChannel,
                                   IdentityRepository identityRepository,
                                   WebSessionPort webSession) {
        this.identityChannel = java.util.Objects.requireNonNull(identityChannel);
        this.identityRepository = java.util.Objects.requireNonNull(identityRepository);
        this.webSession = java.util.Objects.requireNonNull(webSession);
    }

    @Override
    public Result handle(Command command) {
        IdentityChannelPort.AuthenticationDecision decision = identityChannel.authenticate(command.proof());
        if (!decision.authenticated()) {
            throw new ApplicationException(ApplicationErrorCode.AUTH_REQUIRED,
                    "authentication failed", false, Map.of());
        }
        var userId = decision.userId().orElseThrow();
        UserAccount account = identityRepository.findUser(userId).orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.AUTH_REQUIRED, "authentication failed", false, Map.of()));
        if (!account.canAuthenticate()) {
            account.requireActive();
        }
        var memberships = identityRepository.findActiveMemberships(userId);
        if (memberships.size() != 1) {
            throw new ApplicationException(ApplicationErrorCode.FORBIDDEN,
                    "authentication requires an explicit tenant selection", false, Map.of());
        }
        Membership membership = memberships.get(0);
        Tenant tenant = identityRepository.findTenant(membership.tenantId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.AUTH_REQUIRED, "authentication failed", false, Map.of()));
        IdentityPolicy.requireActivePrincipal(tenant, account, membership);
        PrincipalRef principalRef = new PrincipalRef(membership.tenantId(), userId);
        WebSessionPort.IssuedSession session = webSession.issue(principalRef, command.context().requestedAt());
        return new Result(new ResolvedPrincipal(principalRef, session.sessionId(),
                java.util.Set.of(membership.role()), session.expiresAt()), session);
    }
}
