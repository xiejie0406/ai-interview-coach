package com.aiinterviewcoach.adapters.outbound.storage;

import com.aiinterviewcoach.adapters.outbound.AdapterUnavailableException;
import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;

/** 安全默认对象存储：不创建 bucket client，不接收或保存任何音频字节。 */
public final class UnavailableObjectStorageAdapter implements ObjectStorageAdapter, ObjectStoragePort {
    public static final String ADAPTER_ID = "object-storage-unavailable";
    public static final String REASON_CODE = "OBJECT_STORAGE_NOT_CONFIGURED";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String reasonCode() {
        return REASON_CODE;
    }

    @Override
    public UploadHandle beginUpload(BeginUpload request) {
        throw unavailable();
    }

    @Override
    public void writeChunk(UploadHandle handle, StorageChunk chunk) {
        throw unavailable();
    }

    @Override
    public StoredObject completeUpload(UploadHandle handle) {
        throw unavailable();
    }

    @Override
    public void abortUpload(UploadHandle handle, String reasonCode) {
        throw unavailable();
    }

    @Override
    public DeleteResult delete(DeleteRequest request) {
        throw unavailable();
    }

    private AdapterUnavailableException unavailable() {
        return new AdapterUnavailableException("object-storage");
    }
}
