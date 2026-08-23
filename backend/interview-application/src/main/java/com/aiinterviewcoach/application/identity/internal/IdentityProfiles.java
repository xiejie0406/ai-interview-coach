package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.application.identity.ProfileContentDigest;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;

/** 未申报值保持显式 unknown；不把缺失资料解释成经验、岗位或级别事实。 */
final class IdentityProfiles {

    static final String NOT_DECLARED = "NOT_DECLARED";

    private IdentityProfiles() {
    }

    static ProfileVersion initial(
            ResourceId versionId,
            TenantId tenantId,
            UserAccount account,
            Instant effectiveAt
    ) {
        return create(versionId, account.version(), tenantId, account.id(), NOT_DECLARED, NOT_DECLARED,
                NOT_DECLARED, NOT_DECLARED, account.locale(), account.timeZone(), effectiveAt);
    }

    static ProfileVersion next(
            ResourceId versionId,
            AggregateVersion accountVersion,
            TenantId tenantId,
            UserId userId,
            String targetRole,
            String targetLevel,
            String javaExperienceBand,
            String aiExperienceBand,
            String locale,
            String timeZone,
            Instant effectiveAt
    ) {
        return create(versionId, accountVersion, tenantId, userId, targetRole, targetLevel,
                javaExperienceBand, aiExperienceBand, locale, timeZone, effectiveAt);
    }

    static String exactJavaYears(int years) {
        return "YEARS_" + years;
    }

    private static ProfileVersion create(
            ResourceId versionId,
            AggregateVersion accountVersion,
            TenantId tenantId,
            UserId userId,
            String targetRole,
            String targetLevel,
            String javaExperienceBand,
            String aiExperienceBand,
            String locale,
            String timeZone,
            Instant effectiveAt
    ) {
        final int versionNo;
        try {
            versionNo = Math.toIntExact(accountVersion.value());
        } catch (ArithmeticException exception) {
            throw new DomainException(DomainErrorCode.INVALID_STATE,
                    "account version cannot be represented as a profile version");
        }
        if (versionNo <= 0) {
            throw new DomainException(DomainErrorCode.INVALID_STATE,
                    "profile version requires a persisted account revision");
        }
        String contentHash = ProfileContentDigest.compute(targetRole, targetLevel, javaExperienceBand,
                aiExperienceBand, locale, timeZone);
        return new ProfileVersion(new ImmutableVersionRef(versionId, versionNo, contentHash),
                tenantId, userId, targetRole, targetLevel, javaExperienceBand, aiExperienceBand,
                locale, timeZone, effectiveAt);
    }
}
