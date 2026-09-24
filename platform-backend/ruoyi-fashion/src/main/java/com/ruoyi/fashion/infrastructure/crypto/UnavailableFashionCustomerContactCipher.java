package com.ruoyi.fashion.infrastructure.crypto;

import com.ruoyi.common.exception.ServiceException;

public final class UnavailableFashionCustomerContactCipher implements FashionCustomerContactCipher {
    @Override
    public String encrypt(long customerId, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        throw new ServiceException("客户联系电话加密密钥未配置，禁止保存明文联系电话", 503);
    }

    @Override
    public String decrypt(long customerId, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        throw new ServiceException("客户联系电话加密密钥未配置，无法读取联系电话", 503);
    }
}
