package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** 认证成功后轮换/签发服务端 Cookie Session；结果不得作为 bearer token JSON 返回。 */
@FunctionalInterface
public interface AuthenticateUser {

    Result handle(Command command);

    record Command(IdentityChannelPort.AuthenticationProof proof, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(proof, "authenticationProof");
            DomainPreconditions.requireNonNull(context, "operationContext");
        }
    }

    record Result(ResolvedPrincipal principal, WebSessionPort.IssuedSession issuedSession) {
        public Result {
            DomainPreconditions.requireNonNull(principal, "resolvedPrincipal");
            DomainPreconditions.requireNonNull(issuedSession, "issuedSession");
        }
    }
}
