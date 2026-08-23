package com.aiinterviewcoach.application.identity.internal;

import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Optional;

/** 注册渠道归一化标识的唯一性事实；实现必须与 User/Tenant 创建使用同一本地事务。 */
public interface RegistrationIdentifierPort {

    Optional<UserId> findOwner(String normalizedIdentifierHash);

    void bind(String normalizedIdentifierHash, UserId userId);
}
