package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.AccountView;
import com.aiinterviewcoach.domain.identity.IdentityPolicy;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.UserAccount;

import java.util.Optional;

final class IdentityViews {

    private IdentityViews() {
    }

    static AccountView personalOwner(
            UserAccount account,
            Tenant tenant,
            Membership membership,
            Optional<ProfileVersion> latestProfile
    ) {
        IdentityPolicy.requireActivePrincipal(tenant, account, membership);
        IdentityPolicy.requirePersonalOwner(tenant, membership);
        return new AccountView(account.id(), tenant.id(), account.displayName(), account.locale(),
                account.timeZone(), account.status(), account.version(), latestProfile);
    }
}
