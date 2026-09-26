package com.ruoyi.fashion.application.material.port;

import com.ruoyi.common.exception.ServiceException;

public interface FashionObjectStoragePort {
    StoredFashionObject putIfAbsent(String objectKey, byte[] content, String contentType);

    /**
     * 读取私有对象只供已经完成业务归属校验的服务使用。默认拒绝，保留测试替身的函数式接口兼容性。
     */
    default byte[] read(String objectKey) {
        throw new ServiceException("当前对象存储不支持读取");
    }
}
