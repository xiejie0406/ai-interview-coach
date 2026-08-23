package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.AccountView;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.identity.GetCurrentAccount;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultGetCurrentAccount implements GetCurrentAccount {

    private final IdentityRepository repository;
    private final ActivePrincipalGuard principal;

    public DefaultGetCurrentAccount(IdentityRepository repository, ActivePrincipalGuard principal) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.principal = java.util.Objects.requireNonNull(principal);
    }

    @Override
    public AccountView handle(Query query) {
        var owner = principal.requireActive(query.context().principal());
        var account = repository.findUser(owner.userId()).orElseThrow(DefaultGetCurrentAccount::notFound);
        var tenant = repository.findTenant(owner.tenantId()).orElseThrow(DefaultGetCurrentAccount::notFound);
        var membership = repository.findMembership(owner.tenantId(), owner.userId())
                .orElseThrow(DefaultGetCurrentAccount::notFound);
        return IdentityViews.personalOwner(account, tenant, membership,
                repository.latestProfile(owner.tenantId(), owner.userId()));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "current account was not found", false, Map.of());
    }
}
