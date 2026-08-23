package com.aiinterviewcoach.application.integration.port;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.voice.StorageObjectRef;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/** 私有对象存储端口；object key、签名 URL 和厂商响应不得越过 application 边界。 */
public interface ObjectStoragePort {

    UploadHandle beginUpload(BeginUpload request);

    void writeChunk(UploadHandle handle, StorageChunk chunk);

    StoredObject completeUpload(UploadHandle handle);

    void abortUpload(UploadHandle handle, String reasonCode);

    DeleteResult delete(DeleteRequest request);

    record BeginUpload(TenantId tenantId, ResourceId artifactId, String codec,
                       long maximumBytes, Instant expiresAt) {
        public BeginUpload {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            codec = DomainPreconditions.requireText(codec, "audioCodec");
            DomainPreconditions.require(maximumBytes > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "maximum upload bytes must be positive");
            DomainPreconditions.requireNonNull(expiresAt, "artifactExpiresAt");
        }
    }

    record UploadHandle(TenantId tenantId, ResourceId artifactId, String opaqueHandle) {
        public UploadHandle {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            opaqueHandle = DomainPreconditions.requireText(opaqueHandle, "uploadHandle");
        }

        @Override
        public String toString() {
            return "UploadHandle[tenantId=" + tenantId + ", artifactId=" + artifactId
                    + ", opaqueHandle=<redacted>]";
        }
    }

    final class StorageChunk {
        private final long sequence;
        private final byte[] bytes;
        private final boolean endOfInput;

        public StorageChunk(long sequence, byte[] bytes, boolean endOfInput) {
            DomainPreconditions.require(sequence > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "storage chunk sequence must be positive");
            this.sequence = sequence;
            this.bytes = Arrays.copyOf(DomainPreconditions.requireNonNull(bytes, "storageChunk"), bytes.length);
            DomainPreconditions.require(this.bytes.length > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "storage chunk must not be empty");
            this.endOfInput = endOfInput;
        }

        public long sequence() { return sequence; }
        public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
        public boolean endOfInput() { return endOfInput; }

        @Override
        public String toString() {
            return "StorageChunk[sequence=" + sequence + ", bytes=<redacted:"
                    + bytes.length + ">, endOfInput=" + endOfInput + "]";
        }
    }

    record StoredObject(StorageObjectRef objectRef, long bytes, String contentHash) {
        public StoredObject {
            DomainPreconditions.requireNonNull(objectRef, "storageObjectRef");
            DomainPreconditions.require(bytes > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "stored object bytes must be positive");
            contentHash = DomainPreconditions.requireText(contentHash, "storedObjectContentHash");
        }
    }

    record DeleteRequest(TenantId tenantId, ResourceId artifactId, StorageObjectRef objectRef) {
        public DeleteRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            DomainPreconditions.requireNonNull(objectRef, "storageObjectRef");
        }
    }

    record DeleteResult(boolean deleted, Optional<String> receiptHash, Optional<String> failureCode) {
        public DeleteResult {
            receiptHash = receiptHash == null ? Optional.empty() : receiptHash;
            failureCode = failureCode == null ? Optional.empty() : failureCode;
            DomainPreconditions.require(deleted != failureCode.isPresent(), DomainErrorCode.INVALID_ARGUMENT,
                    "object deletion result is inconsistent");
        }
    }
}
