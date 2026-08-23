package com.ruoyi.interview.infrastructure.persistence.shared;

import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;

/** 未配置业务密钥时显式失败，禁止把敏感业务字段降级为明文。 */
public final class UnavailableSensitiveEnvelopeCipher implements SensitiveEnvelopeCipher {
    @Override
    public EncryptedEnvelope encrypt(TenantId tenantId, String aadBinding, String plaintext) {
        throw new AdapterUnavailableException("interview.persistence.cipher");
    }

    @Override
    public String decrypt(TenantId tenantId, String aadBinding, EncryptedEnvelope envelope) {
        throw new AdapterUnavailableException("interview.persistence.cipher");
    }
}
