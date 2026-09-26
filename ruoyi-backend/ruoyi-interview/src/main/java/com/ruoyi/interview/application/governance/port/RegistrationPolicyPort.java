package com.ruoyi.interview.application.governance.port;

import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;

import java.time.Instant;
import java.util.Map;

/** 迁移期政策读取端口；不创建账号、会话或权限。 */
@FunctionalInterface
public interface RegistrationPolicyPort {
    Map<ConsentPurpose, ImmutableVersionRef> requiredPolicies(String locale, Instant at);
}
