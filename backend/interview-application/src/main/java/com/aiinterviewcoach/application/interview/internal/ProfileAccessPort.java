package com.aiinterviewcoach.application.interview.internal;

import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

/** 服务端核对 profile immutable version 的 tenant/owner 归属。 */
@FunctionalInterface
public interface ProfileAccessPort {

    boolean belongsTo(TenantId tenantId, UserId userId, ImmutableVersionRef profileVersion);
}
