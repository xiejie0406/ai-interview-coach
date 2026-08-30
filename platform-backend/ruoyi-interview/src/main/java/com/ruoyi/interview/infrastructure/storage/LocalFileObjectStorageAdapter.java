package com.ruoyi.interview.infrastructure.storage;

import com.ruoyi.interview.infrastructure.provider.AudioArtifactSource;
import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.domain.platform.ArtifactRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.voice.StorageObjectRef;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 本地验收专用私有音频存储；引用和绝对路径不会通过 REST 暴露。 */
public final class LocalFileObjectStorageAdapter
        implements ObjectStorageAdapter, ObjectStoragePort, AudioArtifactSource {
    private final Path root;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Metadata> metadata = new ConcurrentHashMap<>();

    public LocalFileObjectStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try { Files.createDirectories(this.root); }
        catch (IOException exception) { throw new IllegalStateException("无法创建本地音频目录", exception); }
    }

    @Override public String adapterId() { return "local-private-audio-storage"; }
    @Override public boolean available() { return true; }
    @Override public String reasonCode() { return "AVAILABLE"; }

    @Override
    public UploadHandle beginUpload(BeginUpload request) {
        try {
            Path tenant = safeTenant(request.tenantId());
            Files.createDirectories(tenant);
            String token = UUID.randomUUID().toString();
            Path temporary = tenant.resolve(request.artifactId().value() + "." + token + ".part").normalize();
            requireWithinRoot(temporary);
            OutputStream stream = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            pending.put(token, new Pending(request, temporary, stream));
            return new UploadHandle(request.tenantId(), request.artifactId(), token);
        } catch (IOException exception) {
            throw new IllegalStateException("无法开始本地音频上传", exception);
        }
    }

    @Override
    public void writeChunk(UploadHandle handle, StorageChunk chunk) {
        Pending upload = requirePending(handle);
        synchronized (upload) {
            if (chunk.sequence() != upload.nextSequence) throw new IllegalArgumentException("音频分片序号不连续");
            long nextBytes = upload.bytes + chunk.bytes().length;
            if (nextBytes > upload.request.maximumBytes()) throw new IllegalArgumentException("音频超过服务端字节限制");
            try { upload.stream.write(chunk.bytes()); }
            catch (IOException exception) { throw new IllegalStateException("本地音频写入失败", exception); }
            upload.bytes = nextBytes;
            upload.nextSequence++;
        }
    }

    @Override
    public StoredObject completeUpload(UploadHandle handle) {
        Pending upload = requirePending(handle);
        synchronized (upload) {
            try {
                upload.stream.close();
                if (upload.bytes <= 0) throw new IllegalArgumentException("音频内容为空");
                Path target = safeTenant(handle.tenantId()).resolve(handle.artifactId().value() + ".audio").normalize();
                requireWithinRoot(target);
                if (Files.exists(target)) {
                    throw new IllegalStateException("音频 Artifact 已存在，禁止覆盖");
                }
                Files.move(upload.temporary, target, StandardCopyOption.ATOMIC_MOVE);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(target)));
                metadata.put(key(handle.tenantId(), handle.artifactId()),
                        new Metadata(target, upload.request.codec()));
                pending.remove(handle.opaqueHandle(), upload);
                return new StoredObject(new StorageObjectRef(handle.artifactId().value()), upload.bytes, hash);
            } catch (Exception exception) {
                pending.remove(handle.opaqueHandle(), upload);
                try {
                    Files.deleteIfExists(upload.temporary);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw new IllegalStateException("无法完成本地音频上传", exception);
            }
        }
    }

    @Override
    public void abortUpload(UploadHandle handle, String reasonCode) {
        Pending upload = pending.remove(handle.opaqueHandle());
        if (upload == null) return;
        try { upload.stream.close(); Files.deleteIfExists(upload.temporary); }
        catch (IOException exception) { throw new IllegalStateException("无法清理未完成音频", exception); }
    }

    @Override
    public DeleteResult delete(DeleteRequest request) {
        requireOwnedReference(request.artifactId(), request.objectRef());
        Metadata item = metadata.remove(key(request.tenantId(), request.artifactId()));
        Path path = item == null ? safeTenant(request.tenantId())
                .resolve(request.artifactId().value() + ".audio").normalize() : item.path;
        requireWithinRoot(path);
        try {
            Files.deleteIfExists(path);
            return new DeleteResult(true, Optional.empty(), Optional.empty());
        } catch (IOException exception) {
            return new DeleteResult(false, Optional.empty(), Optional.of("LOCAL_AUDIO_DELETE_FAILED"));
        }
    }

    @Override
    public AudioContent load(TenantId tenantId, ArtifactRef artifactRef) {
        Metadata item = metadata.get(key(tenantId, artifactRef.artifactId()));
        Path path = item == null ? safeTenant(tenantId)
                .resolve(artifactRef.artifactId().value() + ".audio").normalize() : item.path;
        requireWithinRoot(path);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String codec = item == null ? "audio/webm;codecs=opus" : item.codec;
            return new AudioContent(bytes, format(codec), codec, 48_000, 16, 1);
        } catch (IOException exception) {
            throw new IllegalStateException("本地音频不存在", exception);
        }
    }

    private Pending requirePending(UploadHandle handle) {
        Pending upload = pending.get(handle.opaqueHandle());
        if (upload == null || !upload.request.tenantId().equals(handle.tenantId())
                || !upload.request.artifactId().equals(handle.artifactId())) {
            throw new IllegalArgumentException("音频上传句柄无效");
        }
        return upload;
    }

    private Path safeTenant(TenantId tenantId) {
        Path path = root.resolve(tenantId.value()).normalize();
        requireWithinRoot(path);
        return path;
    }

    private void requireWithinRoot(Path path) {
        if (!path.startsWith(root)) throw new IllegalArgumentException("本地音频路径越界");
    }

    private static void requireOwnedReference(ResourceId artifactId, StorageObjectRef objectRef) {
        if (!artifactId.value().equals(objectRef.opaqueReference())) {
            throw new IllegalArgumentException("音频对象引用与 Artifact 不匹配");
        }
    }

    private static String key(TenantId tenantId, ResourceId artifactId) {
        return tenantId.value() + "/" + artifactId.value();
    }

    private static String format(String codec) {
        if (codec.contains("ogg")) return "ogg";
        if (codec.contains("wav")) return "wav";
        return "webm";
    }

    private static final class Pending {
        private final BeginUpload request;
        private final Path temporary;
        private final OutputStream stream;
        private long nextSequence = 1;
        private long bytes;
        private Pending(BeginUpload request, Path temporary, OutputStream stream) {
            this.request = request; this.temporary = temporary; this.stream = stream;
        }
    }
    private record Metadata(Path path, String codec) { }
}

