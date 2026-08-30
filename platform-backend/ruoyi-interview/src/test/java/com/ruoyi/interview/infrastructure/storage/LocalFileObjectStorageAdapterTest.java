package com.ruoyi.interview.infrastructure.storage;

import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.domain.platform.ArtifactRef;
import com.ruoyi.interview.domain.platform.DataClassification;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.voice.StorageObjectRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileObjectStorageAdapterTest {
    @TempDir
    Path root;

    @Test
    void storesByTenantRejectsOverwriteAndChecksOwnedReference() {
        LocalFileObjectStorageAdapter storage = new LocalFileObjectStorageAdapter(root);
        TenantId tenant = TenantId.of("tenant-a");
        ResourceId artifact = ResourceId.of("artifact-a");
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        var request = new ObjectStoragePort.BeginUpload(
                tenant, artifact, "audio/webm;codecs=opus", 32, expiry);
        var handle = storage.beginUpload(request);
        storage.writeChunk(handle, new ObjectStoragePort.StorageChunk(1, new byte[]{1, 2, 3}, true));
        var stored = storage.completeUpload(handle);

        var content = storage.load(tenant, new ArtifactRef(
                artifact, "ANSWER_TRANSCRIPTION", DataClassification.HIGHLY_SENSITIVE, expiry));
        assertArrayEquals(new byte[]{1, 2, 3}, content.bytes());

        var duplicate = storage.beginUpload(request);
        storage.writeChunk(duplicate, new ObjectStoragePort.StorageChunk(1, new byte[]{9}, true));
        assertThrows(IllegalStateException.class, () -> storage.completeUpload(duplicate));
        assertThrows(IllegalArgumentException.class, () -> storage.delete(
                new ObjectStoragePort.DeleteRequest(tenant, artifact,
                        new StorageObjectRef("another-artifact"))));

        assertTrue(storage.delete(new ObjectStoragePort.DeleteRequest(
                tenant, artifact, stored.objectRef())).deleted());
    }

    @Test
    void rejectsOutOfOrderChunksAndCleansAbortedUpload() {
        LocalFileObjectStorageAdapter storage = new LocalFileObjectStorageAdapter(root);
        var request = new ObjectStoragePort.BeginUpload(TenantId.of("tenant-b"),
                ResourceId.of("artifact-b"), "audio/wav", 16, Instant.parse("2030-01-01T00:00:00Z"));
        var handle = storage.beginUpload(request);
        assertThrows(IllegalArgumentException.class, () -> storage.writeChunk(
                handle, new ObjectStoragePort.StorageChunk(2, new byte[]{1}, false)));
        storage.abortUpload(handle, "TEST_ABORT");
        assertThrows(IllegalArgumentException.class, () -> storage.writeChunk(
                handle, new ObjectStoragePort.StorageChunk(1, new byte[]{1}, false)));
    }
}
