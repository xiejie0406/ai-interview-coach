package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

/** Identity owner 的 persistence port；实现查询必须显式带 tenant 或全局白名单语义。 */
public interface IdentityRepository {

    Optional<UserAccount> findUser(UserId userId);

    Optional<UserAccount> findUserByIdentifierHash(String normalizedIdentifierHash);

    Optional<Tenant> findTenant(TenantId tenantId);

    Optional<Membership> findMembership(TenantId tenantId, UserId userId);

    List<Membership> findActiveMemberships(UserId userId);

    /** 只返回该 personal tenant owner 的最新不可变档案版本。 */
    Optional<ProfileVersion> latestProfile(TenantId tenantId, UserId userId);

    void saveUser(UserAccount account);

    void saveTenant(Tenant tenant);

    void saveMembership(Membership membership);

    /**
     * 在账号 CAS 已推进后的同一事务追加档案版本。实现必须验证 tenant owner、active membership、
     * accountVersion 与 profile.versionNo 一致，且不得更新既有档案行。
     */
    void appendProfile(ProfileVersion profile, AggregateVersion accountVersion);
}
