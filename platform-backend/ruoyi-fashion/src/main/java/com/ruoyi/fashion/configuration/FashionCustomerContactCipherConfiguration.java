package com.ruoyi.fashion.configuration;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ruoyi.fashion.infrastructure.crypto.AesGcmFashionCustomerContactCipher;
import com.ruoyi.fashion.infrastructure.crypto.FashionCustomerContactCipher;
import com.ruoyi.fashion.infrastructure.crypto.UnavailableFashionCustomerContactCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FashionCustomerContactCipherProperties.class)
public class FashionCustomerContactCipherConfiguration {
    @Bean
    public FashionCustomerContactCipher fashionCustomerContactCipher(FashionCustomerContactCipherProperties properties) {
        if (blank(properties.getActiveKeyId()) || blank(properties.getActiveKeyBase64())) {
            return new UnavailableFashionCustomerContactCipher();
        }
        LinkedHashMap<String, byte[]> keys = new LinkedHashMap<>();
        keys.put(properties.getActiveKeyId(), decode(properties.getActiveKeyBase64()));
        if (!blank(properties.getPreviousKeyId()) && !blank(properties.getPreviousKeyBase64())) {
            keys.put(properties.getPreviousKeyId(), decode(properties.getPreviousKeyBase64()));
        }
        return new AesGcmFashionCustomerContactCipher(properties.getActiveKeyId(), Map.copyOf(keys));
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("客户联系电话密钥不是合法 Base64", exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
