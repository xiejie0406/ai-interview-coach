package com.ruoyi.fashion.infrastructure.crypto;

public interface FashionCustomerContactCipher {
    String encrypt(long customerId, String plaintext);

    String decrypt(long customerId, String ciphertext);
}
