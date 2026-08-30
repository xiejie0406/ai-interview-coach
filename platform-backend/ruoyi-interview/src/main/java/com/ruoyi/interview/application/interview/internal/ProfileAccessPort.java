package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

/** 服务端核对 profile immutable version 的 tenant/owner 归属。 */
@FunctionalInterface
public interface ProfileAccessPort {

    boolean belongsTo(TenantId tenantId, UserId userId, ImmutableVersionRef profileVersion);
}
