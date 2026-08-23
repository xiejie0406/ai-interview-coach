package com.ruoyi.interview.domain.voice;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

/** 只在服务端使用的对象引用；不得作为普通 REST DTO 或日志字段暴露。 */
public record StorageObjectRef(String opaqueReference) {

    public StorageObjectRef {
        opaqueReference = DomainPreconditions.requireText(opaqueReference, "storageObjectReference");
        DomainPreconditions.require(opaqueReference.length() <= 2048, DomainErrorCode.INVALID_ARGUMENT,
                "storage object reference exceeds maximum length");
    }

    @Override
    public String toString() {
        return "StorageObjectRef[opaqueReference=<redacted>]";
    }
}
