package com.ruoyi.interview.infrastructure.persistence.shared;

import com.ruoyi.interview.domain.platform.TenantId;

/**
 * 生产环境必须提供的敏感字段加密端口。当前仓库没有默认实现；缺少实现时依赖它的
 * Repository 无法装配，从而禁止把题目、回答、转写、显示名或对象存储引用降级为明文。
 */
public interface SensitiveEnvelopeCipher {

    EncryptedEnvelope encrypt(TenantId tenantId, String aadBinding, String plaintext);

    String decrypt(TenantId tenantId, String aadBinding, EncryptedEnvelope envelope);
}

