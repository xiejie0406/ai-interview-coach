package com.ruoyi.interview.infrastructure.storage;

import com.ruoyi.interview.infrastructure.OutboundAdapterAvailability;

/** 仅为 adapter 侧标记；未来必须先由 application 定义 ObjectStoragePort，才能增加上传/删除实现。 */
public interface ObjectStorageAdapter extends OutboundAdapterAvailability {
}


