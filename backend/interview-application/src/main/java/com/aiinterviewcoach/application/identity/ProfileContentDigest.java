package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** ProfileVersion 的稳定内容摘要契约；不包含 ID、版本号或生效时间。 */
public final class ProfileContentDigest {

    private static final String FORMAT = "identity.profile-content.v1";

    private ProfileContentDigest() {
    }

    public static String compute(
            String targetRole,
            String targetLevel,
            String javaExperienceBand,
            String aiExperienceBand,
            String locale,
            String timeZone
    ) {
        return ServerSideDigest.sha256(FORMAT,
                DomainPreconditions.requireText(targetRole, "targetRole"),
                DomainPreconditions.requireText(targetLevel, "targetLevel"),
                DomainPreconditions.requireText(javaExperienceBand, "javaExperienceBand"),
                DomainPreconditions.requireText(aiExperienceBand, "aiExperienceBand"),
                DomainPreconditions.requireText(locale, "locale"),
                DomainPreconditions.requireText(timeZone, "timeZone"));
    }

    public static String compute(ProfileVersion profile) {
        DomainPreconditions.requireNonNull(profile, "profileVersion");
        return compute(profile.targetRole(), profile.targetLevel(), profile.javaExperienceBand(),
                profile.aiExperienceBand(), profile.locale(), profile.timeZone());
    }

    public static void requireMatches(ProfileVersion profile) {
        DomainPreconditions.require(compute(profile).equals(profile.versionRef().contentHash()),
                DomainErrorCode.INVALID_ARGUMENT, "profile content hash does not match its immutable content");
    }
}
