package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.identity.Logout;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

/** 当前服务端 Session 的幂等吊销；sessionId 只能来自可信 Cookie 解析结果。 */
public final class DefaultLogout implements Logout {

    private final ActivePrincipalGuard principal;
    private final WebSessionPort sessions;
    private final IdempotencyGuard idempotency;
    private final TransactionPort transaction;

    public DefaultLogout(ActivePrincipalGuard principal, WebSessionPort sessions,
                         IdempotencyGuard idempotency, TransactionPort transaction) {
        this.principal = java.util.Objects.requireNonNull(principal);
        this.sessions = java.util.Objects.requireNonNull(sessions);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public void handle(Command command) {
        transaction.required(() -> {
            var owner = principal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("identity.logout", command.sessionId().value());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "identity.logout", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "logout is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                return;
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_FAILURE) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                        "logout replays a recorded failure", false, Map.of());
            }
            sessions.revoke(owner.tenantId(), command.sessionId(), command.context().requestedAt());
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "identity.logout", requestHash, Map.of("sessionId", command.sessionId().value()),
                    204, command.context()));
        });
    }
}
