package com.ruoyi.interview.application.governance.port;

import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;

/** 在 tenant-local FK registry 中登记已由服务端政策配置验证的不可变政策引用。 */
@FunctionalInterface
public interface ConsentPolicyRegistryPort {

    void ensureRegistered(TenantId tenantId, ConsentPurpose purpose,
                          ImmutableVersionRef policyVersion, Instant effectiveFrom);
}
