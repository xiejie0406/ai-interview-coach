package com.aiinterviewcoach.application.governance.internal;

import com.aiinterviewcoach.application.governance.port.ConsentRepository;
import com.aiinterviewcoach.application.governance.port.ConsentPolicyRegistryPort;
import com.aiinterviewcoach.application.identity.port.RegistrationConsentPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.domain.governance.ConsentRecord;

/** 在调用方已经打开的注册事务中追加 Governance-owned Consent facts。 */
public final class DefaultRegistrationConsentPort implements RegistrationConsentPort {

    private final ConsentRepository repository;
    private final IdGeneratorPort idGenerator;
    private final ConsentPolicyRegistryPort policies;

    public DefaultRegistrationConsentPort(ConsentRepository repository, IdGeneratorPort idGenerator,
                                          ConsentPolicyRegistryPort policies) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.policies = java.util.Objects.requireNonNull(policies);
    }

    @Override
    public void append(Command command) {
        command.acceptedPolicies().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    policies.ensureRegistered(command.tenantId(), entry.getKey(), entry.getValue(),
                            command.acceptedAt());
                    repository.append(ConsentRecord.granted(idGenerator.nextResourceId(),
                            command.tenantId(), command.userId(), entry.getValue(), entry.getKey(),
                            command.source(), command.acceptedAt()));
                });
    }
}
