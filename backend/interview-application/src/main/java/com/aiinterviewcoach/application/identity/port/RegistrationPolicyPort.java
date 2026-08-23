package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;

import java.time.Instant;
import java.util.Map;

/** 服务端解析当前注册必需政策版本；客户端提交的版本只能用于逐项比对，不能成为权威版本。 */
public interface RegistrationPolicyPort {

    Map<ConsentPurpose, ImmutableVersionRef> requiredPolicies(String locale, Instant at);
}
