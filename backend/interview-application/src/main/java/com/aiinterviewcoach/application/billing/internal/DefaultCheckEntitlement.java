package com.aiinterviewcoach.application.billing.internal;

import com.aiinterviewcoach.application.billing.CheckEntitlement;
import com.aiinterviewcoach.application.billing.EntitlementView;
import com.aiinterviewcoach.application.billing.port.BillingRepository;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.domain.billing.EntitlementState;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;

/** 只读权益检查；最终预留仍必须在同一事务重新 CAS。 */
public final class DefaultCheckEntitlement implements CheckEntitlement {

    private final BillingRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultCheckEntitlement(BillingRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public EntitlementView handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        return repository.findUsableEntitlements(owner.tenantId(), owner.userId(), query.required().unit()).stream()
                .filter(entitlement -> entitlement.state() == EntitlementState.ACTIVE
                        && !query.context().requestedAt().isBefore(entitlement.validFrom())
                        && query.context().requestedAt().isBefore(entitlement.validTo()))
                .filter(entitlement -> entitlement.available().isGreaterThan(query.required())
                        || entitlement.available().value().compareTo(query.required().value()) == 0)
                .findFirst()
                .map(BillingViews::entitlement)
                .orElseThrow(() -> new DomainException(DomainErrorCode.USAGE_EXCEEDED,
                        "no active entitlement has enough available usage"));
    }
}
