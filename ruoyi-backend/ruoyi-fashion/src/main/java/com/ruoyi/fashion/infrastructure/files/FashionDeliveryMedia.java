package com.ruoyi.fashion.infrastructure.files;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.Objects;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.springframework.stereotype.Component;

@FashionModuleEnabled
@Component
public final class FashionDeliveryMedia {
    private final FashionObjectStoragePort storage;

    public FashionDeliveryMedia(FashionObjectStoragePort storage) {
        this.storage = storage;
    }

    public byte[] readVerified(String objectKey, String expectedSha256) {
        if (objectKey == null || expectedSha256 == null || !expectedSha256.matches("[a-f0-9]{64}")) {
            throw new ServiceException("交付图片引用不完整");
        }
        byte[] content = storage.read(objectKey);
        if (content.length == 0 || !Objects.equals(expectedSha256, FashionHashing.sha256(content))) {
            throw new ServiceException("交付图片内容摘要不一致");
        }
        return content;
    }
}
