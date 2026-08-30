package com.ruoyi.interview.application.governance.internal;

import com.ruoyi.interview.application.governance.GrantConsent;
import com.ruoyi.interview.application.governance.port.ConsentRepository;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.governance.ConsentAction;
import com.ruoyi.interview.domain.governance.ConsentRecord;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainException;

import java.util.Map;

/** 同意是 append-only 事实；撤回通过新事实表达，历史记录永不覆盖。 */
public final class DefaultGrantConsent implements GrantConsent {

    private final ActivePrincipalGuard activePrincipal;
    private final ConsentRepository consentRepository;
    private final IdGeneratorPort idGenerator;
    private final IdempotencyGuard idempotency;
    private final TransactionPort transaction;

    public DefaultGrantConsent(ActivePrincipalGuard activePrincipal,
                               ConsentRepository consentRepository,
                               IdGeneratorPort idGenerator,
                               IdempotencyGuard idempotency,
                               TransactionPort transaction) {
        this.activePrincipal = java.util.Objects.requireNonNull(activePrincipal);
        this.consentRepository = java.util.Objects.requireNonNull(consentRepository);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.idempotency = java.util.Objects.requireNonNull(idempotency);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        return transaction.required(() -> {
            var principal = activePrincipal.requireActive(command.context());
            String requestHash = ServerSideDigest.sha256("consent", principal.tenantId().value(),
                    principal.userId().value(), command.purpose().name(), command.action().name(),
                    command.policyVersion().toString(), command.source());
            IdempotencyGuard.Decision decision = idempotency.begin(new IdempotencyGuard.BeginCommand(
                    "governance.consent", requestHash, command.context()));
            if (decision.type() == IdempotencyGuard.DecisionType.IN_PROGRESS) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "consent request is already processing", true, Map.of());
            }
            if (decision.type() == IdempotencyGuard.DecisionType.REPLAY_SUCCESS) {
                String consentId = decision.resourceReferences().get("consentRecordId");
                if (consentId == null) {
                    throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                            "consent replay has no record reference", false, Map.of());
                }
                return new Result(com.ruoyi.interview.domain.platform.ResourceId.of(consentId), command.action());
            }
            var current = consentRepository.current(principal.tenantId(), principal.userId(), command.purpose());
            if (command.action() == ConsentAction.REVOKED && current.isEmpty()) {
                throw new DomainException(DomainErrorCode.CONSENT_REQUIRED,
                        "cannot revoke consent without an existing consent fact");
            }
            var recordId = idGenerator.nextResourceId();
            ConsentRecord record;
            if (command.action() == ConsentAction.GRANTED) {
                record = current.isEmpty()
                        ? ConsentRecord.granted(recordId, principal.tenantId(), principal.userId(),
                        command.policyVersion(), command.purpose(), command.source(), command.context().requestedAt())
                        : ConsentRecord.granted(recordId, principal.tenantId(), principal.userId(),
                        command.policyVersion(), command.purpose(), command.source(), command.context().requestedAt(),
                        current.orElseThrow().id());
            } else {
                record = ConsentRecord.revoked(recordId, principal.tenantId(), principal.userId(),
                        command.policyVersion(), command.purpose(), command.source(), command.context().requestedAt(),
                        current.orElseThrow().id());
            }
            consentRepository.append(record);
            idempotency.succeed(new IdempotencyGuard.CompleteCommand(
                    "governance.consent", requestHash, Map.of("consentRecordId", record.id().value()), 200,
                    command.context()));
            return new Result(record.id(), record.action());
        });
    }
}
