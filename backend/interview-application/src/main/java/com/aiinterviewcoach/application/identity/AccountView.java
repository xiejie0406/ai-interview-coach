package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.UserAccountStatus;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Optional;

/** Identity 对外的当前账号投影；敏感档案只在可信服务端调用链中存在。 */
public record AccountView(
        UserId userId,
        TenantId tenantId,
        String displayName,
        String locale,
        String timeZone,
        UserAccountStatus accountStatus,
        AggregateVersion version,
        Optional<ProfileVersion> latestProfile
) {

    public AccountView {
        DomainPreconditions.requireNonNull(userId, "userId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        displayName = DomainPreconditions.requireText(displayName, "displayName");
        locale = DomainPreconditions.requireText(locale, "locale");
        timeZone = DomainPreconditions.requireText(timeZone, "timeZone");
        DomainPreconditions.requireNonNull(accountStatus, "accountStatus");
        DomainPreconditions.requireNonNull(version, "accountVersion");
        latestProfile = latestProfile == null ? Optional.empty() : latestProfile;
    }

    @Override
    public String toString() {
        return "AccountView[userId=" + userId + ", tenantId=" + tenantId
                + ", displayName=<redacted>, locale=" + locale + ", timeZone=" + timeZone
                + ", accountStatus=" + accountStatus + ", version=" + version
                + ", latestProfile=" + (latestProfile.isPresent() ? "<present>" : "<absent>") + "]";
    }
}
