package com.aiinterviewcoach.domain.identity;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;

/** 用户目标档案的不可变版本，计划只引用其版本快照。 */
public record ProfileVersion(
        ImmutableVersionRef versionRef,
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

    public ProfileVersion {
        DomainPreconditions.requireNonNull(versionRef, "profileVersionRef");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(userId, "userId");
        targetRole = DomainPreconditions.requireText(targetRole, "targetRole");
        targetLevel = DomainPreconditions.requireText(targetLevel, "targetLevel");
        javaExperienceBand = DomainPreconditions.requireText(javaExperienceBand, "javaExperienceBand");
        aiExperienceBand = DomainPreconditions.requireText(aiExperienceBand, "aiExperienceBand");
        locale = DomainPreconditions.requireText(locale, "locale");
        timeZone = DomainPreconditions.requireText(timeZone, "timeZone");
        DomainPreconditions.requireNonNull(effectiveAt, "effectiveAt");
    }

    @Override
    public String toString() {
        return "ProfileVersion[versionRef=" + versionRef + ", tenantId=" + tenantId + ", userId=" + userId
                + ", targetRole=<redacted>, targetLevel=<redacted>, javaExperienceBand=<redacted>"
                + ", aiExperienceBand=<redacted>, locale=" + locale + ", timeZone=" + timeZone
                + ", effectiveAt=" + effectiveAt + "]";
    }
}
