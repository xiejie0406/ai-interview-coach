package com.aiinterviewcoach.adapters.outbound.storage;

import com.aiinterviewcoach.adapters.outbound.OutboundAdapterAvailability;

/** 仅为 adapter 侧标记；未来必须先由 application 定义 ObjectStoragePort，才能增加上传/删除实现。 */
public interface ObjectStorageAdapter extends OutboundAdapterAvailability {
}
