package com.ruoyi.interview.application.governance.internal;

import com.ruoyi.interview.application.governance.port.ConsentQueryPort;
import com.ruoyi.interview.application.governance.port.ConsentRepository;
import com.ruoyi.interview.domain.governance.ConsentPolicy;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.governance.ConsentRecord;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.HashSet;

/** 折叠 append-only 同意历史；冲突事实安全默认拒绝。 */
public final class DefaultConsentQuery implements ConsentQueryPort {

    private final ConsentRepository repository;

    public DefaultConsentQuery(ConsentRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    @Override
    public ConsentDecision current(TenantId tenantId, UserId userId, ConsentPurpose purpose, Instant at) {
        var history = repository.history(tenantId, userId, purpose).stream()
                .filter(record -> !record.effectiveAt().isAfter(at)).toList();
        if (!ConsentPolicy.isGranted(tenantId, userId, purpose, history, at)) {
            return new ConsentDecision(purpose, false, java.util.Optional.empty(),
                    java.util.Optional.empty(), java.util.Optional.empty());
        }
        HashSet<com.ruoyi.interview.domain.platform.ResourceId> superseded = new HashSet<>();
        history.forEach(record -> record.supersededConsentId().ifPresent(superseded::add));
        ConsentRecord current = history.stream()
                .filter(record -> !superseded.contains(record.id()) && record.isGranted())
                .findFirst().orElseThrow();
        ImmutableVersionRef version = current.policyVersion();
        return new ConsentDecision(purpose, true, java.util.Optional.of(current.id()),
                java.util.Optional.of(version), java.util.Optional.of(current.effectiveAt()));
    }
}
