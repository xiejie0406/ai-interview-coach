package com.ruoyi.fashion.application.delivery;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.configuration.FashionDeliveryProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.files.DeliveryFileNames;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class FashionDeliveryService {
    public static final String RENDERER_VERSION = "fashion-delivery-1.0";
    private static final Set<String> TYPES = Set.of("pptx", "csv", "image_zip", "jpg");
    private static final Set<String> PURPOSES = Set.of("customer", "internal_expired");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "csv", "text/csv;charset=UTF-8", "image_zip", "application/zip", "jpg", "image/jpeg");

    private final FashionDeliveryRepository repository;
    private final FashionQuoteDraftService quotes;
    private final FashionObjectStoragePort storage;
    private final FashionIdGenerator ids;
    private final FashionTimeSource time;
    private final FashionDeliveryProperties properties;
    private final TransactionTemplate transaction;
    private final Map<String, FashionDeliveryRenderer> renderers;
    private final FashionTelemetry telemetry;

    public FashionDeliveryService(
            FashionDeliveryRepository repository,
            FashionQuoteDraftService quotes,
            FashionObjectStoragePort storage,
            FashionIdGenerator ids,
            FashionTimeSource time,
            FashionDeliveryProperties properties,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction,
            List<FashionDeliveryRenderer> renderers,
            FashionTelemetry telemetry) {
        this.repository = repository;
        this.quotes = quotes;
        this.storage = storage;
        this.ids = ids;
        this.time = time;
        this.properties = properties;
        this.transaction = transaction;
        this.telemetry = telemetry;
        Map<String, FashionDeliveryRenderer> values = new HashMap<>();
        for (FashionDeliveryRenderer renderer : renderers) {
            if (values.put(renderer.fileType(), renderer) != null) {
                throw new IllegalStateException("交付渲染器类型重复：" + renderer.fileType());
            }
        }
        this.renderers = Map.copyOf(values);
    }

    public DeliveryWorkspace workspace(String quoteId) {
        long id = accessibleQuote(quoteId);
        DeliveryQuoteSnapshot snapshot = requireConfirmed(id);
        return new DeliveryWorkspace(Long.toString(id), snapshot.quoteNo(), snapshot.versionNo(), snapshot.title(),
                snapshot.contentHash(), "confirmed", repository.findByQuote(id).stream()
                        .map(DeliveryFileView::from).toList());
    }

    public DeliveryFileView request(String quoteId, DeliveryRequestCommand command, long operatorId) {
        long id = accessibleQuote(quoteId);
        DeliveryQuoteSnapshot snapshot = requireConfirmed(id);
        String type = command == null ? null : command.fileType();
        String purpose = command == null || command.purpose() == null ? "customer" : command.purpose();
        String renderer = command == null || command.rendererVersion() == null
                ? RENDERER_VERSION : command.rendererVersion();
        if (!TYPES.contains(type) || !renderers.containsKey(type)) throw new ServiceException("导出文件类型无效");
        if (!PURPOSES.contains(purpose)) throw new ServiceException("导出用途无效");
        if (!RENDERER_VERSION.equals(renderer)) throw new ServiceException("渲染版本不受支持");
        String requestKey = requestKey(snapshot, type, purpose, renderer);
        DeliveryFileTask existing = repository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) return DeliveryFileView.from(existing);
        Instant now = time.now();
        DeliveryFileTask task = new DeliveryFileTask(ids.nextId(), id, snapshot.quoteNo(), snapshot.versionNo(),
                snapshot.title(), type, purpose, snapshot.contentHash(), renderer, requestKey, "queued", List.of(),
                0, null, null, null, null, 0, operatorId, now, 1);
        try {
            DeliveryFileTask created = transaction.execute(status -> {
                DeliveryFileTask raced = repository.findByRequestKey(requestKey).orElse(null);
                if (raced != null) return raced;
                repository.insert(task);
                return task;
            });
            telemetry.recordDeliveryEvent(type, "queued", 1);
            return DeliveryFileView.from(created);
        } catch (DataIntegrityViolationException exception) {
            return DeliveryFileView.from(repository.findByRequestKey(requestKey)
                    .orElseThrow(() -> new ServiceException("导出请求并发冲突", 409)));
        }
    }

    public DeliveryFileView retry(String quoteId, String fileId, long rowVersion, long operatorId) {
        long quote = accessibleQuote(quoteId);
        DeliveryFileTask task = requireOwned(fileId, quote);
        if (!"failed".equals(task.status()) || task.retryCount() >= 2) {
            throw new ServiceException("当前导出任务不可重试", 409);
        }
        boolean changed = transaction.execute(status -> repository.requeue(task.id(), rowVersion, operatorId, time.now()));
        if (!changed) throw conflict();
        telemetry.recordDeliveryEvent(task.fileType(), "queued", 1);
        return DeliveryFileView.from(repository.findById(task.id()).orElseThrow());
    }

    public boolean processNext(String workerId) {
        if (workerId == null || !workerId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("workerId 不符合白名单");
        }
        Instant claimedAt = time.now();
        DeliveryFileTask task = transaction.execute(status -> repository.claimNext(
                claimedAt, claimedAt.plus(properties.getLeaseDuration())).orElse(null));
        if (task == null) return false;
        telemetry.recordDeliveryEvent(task.fileType(), "running", 1);
        try {
            DeliveryQuoteSnapshot snapshot = requireConfirmed(task.quoteId());
            if (!task.quoteHash().equals(snapshot.contentHash())) {
                throw new ServiceException("导出任务与已确认报价摘要不一致");
            }
            FashionDeliveryRenderer renderer = renderers.get(task.fileType());
            if (renderer == null || !RENDERER_VERSION.equals(task.rendererVersion())) {
                throw new ServiceException("导出渲染器不可用");
            }
            List<GeneratedDeliveryArtifact> generated = renderer.render(snapshot);
            List<DeliveryArtifact> artifacts = persist(task, generated);
            Instant finished = time.now();
            boolean completed = transaction.execute(status -> repository.complete(task.id(), task.rowVersion(),
                    artifacts, 0L, finished));
            if (!completed) throw new ServiceException("导出任务租约已失效", 409);
            telemetry.recordDeliveryCompletion(task.fileType(), "success", Duration.between(claimedAt, finished));
        } catch (Exception exception) {
            Instant failedAt = time.now();
            int retry = Math.min(2, task.retryCount() + 1);
            Instant next = retry < 2 ? failedAt.plusSeconds(5L * retry) : null;
            transaction.executeWithoutResult(status -> repository.fail(task.id(), task.rowVersion(), retry, next,
                    safeError(exception), 0L, failedAt));
            telemetry.recordDeliveryCompletion(task.fileType(), "failed", nonNegative(claimedAt, failedAt));
        }
        return true;
    }

    public DeliveryDownload download(String quoteId, String fileId, int artifactNo) {
        long quote = accessibleQuote(quoteId);
        DeliveryFileTask task = requireOwned(fileId, quote);
        if (!"success".equals(task.status()) || artifactNo < 1 || artifactNo > task.artifacts().size()) {
            throw new ServiceException("交付文件不存在或尚未生成", 404);
        }
        DeliveryArtifact artifact = task.artifacts().get(artifactNo - 1);
        validateArtifactMetadata(task.fileType(), artifact);
        byte[] content = storage.read(artifact.objectKey());
        if (content.length != artifact.byteSize() || !FashionHashing.sha256(content).equals(artifact.sha256())) {
            throw new ServiceException("交付文件摘要校验失败");
        }
        validatePayload(task.fileType(), artifact.fileName(), artifact.contentType(), content);
        return new DeliveryDownload(task.id(), task.rowVersion(), artifact, content);
    }

    /** 仅在响应流完整写出后调用；下载计数不是“客户已收到”的业务承诺。 */
    public void markDownloaded(DeliveryDownload download, long operatorId) {
        repository.markDownloaded(download.taskId(), download.taskRowVersion(), operatorId, time.now());
        telemetry.recordDeliveryEvent(extensionType(download.artifact().fileName()), "downloaded", 1);
    }

    public DeliveryFileView extendRetention(String quoteId, String fileId, long rowVersion,
            Instant retainUntil, long operatorId) {
        long quote = accessibleQuote(quoteId);
        DeliveryFileTask task = requireOwned(fileId, quote);
        Instant now = time.now();
        if (retainUntil == null || !retainUntil.isAfter(now) || retainUntil.isAfter(now.plus(Duration.ofDays(3650)))) {
            throw new ServiceException("保留延长期限必须在未来十年内");
        }
        boolean changed = transaction.execute(status -> repository.extendRetention(task.id(), rowVersion,
                retainUntil, operatorId, now));
        if (!changed) throw conflict();
        return DeliveryFileView.from(repository.findById(task.id()).orElseThrow());
    }

    private List<DeliveryArtifact> persist(DeliveryFileTask task, List<GeneratedDeliveryArtifact> generated) {
        if (generated == null || generated.isEmpty() || generated.size() > 20) {
            throw new ServiceException("交付渲染器未生成有效文件");
        }
        List<DeliveryArtifact> artifacts = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (GeneratedDeliveryArtifact item : generated) {
            validatePayload(task.fileType(), item.fileName(), item.contentType(), item.content());
            if (!names.add(item.fileName())) throw new ServiceException("交付文件名重复");
            String sha = FashionHashing.sha256(item.content());
            String key = "delivery/" + task.quoteId() + "/" + task.quoteHash() + "/"
                    + task.rendererVersion() + "/" + task.fileType() + "/" + sha
                    + DeliveryFileNames.extension(task.fileType());
            StoredFashionObject stored = storage.putIfAbsent(key, item.content(), item.contentType());
            if (!stored.sha256().equals(sha) || stored.bytes() != item.content().length) {
                throw new ServiceException("对象存储返回的交付摘要不一致");
            }
            artifacts.add(new DeliveryArtifact(item.fileName(), stored.objectKey(), item.contentType(), sha,
                    item.content().length, item.pageCount(), item.role(), item.skuCodes(), item.sourceMode(),
                    item.reviewStatus(), null));
        }
        return List.copyOf(artifacts);
    }

    private void validatePayload(String type, String fileName, String contentType, byte[] content) {
        if (!TYPES.contains(type) || fileName == null || fileName.length() > 180
                || fileName.matches(".*[\\r\\n\\\\/].*") || fileName.contains("..")
                || !fileName.toLowerCase().endsWith(DeliveryFileNames.extension(type))) {
            throw new ServiceException("交付文件名不符合白名单");
        }
        if (!CONTENT_TYPES.get(type).equals(contentType) || content == null || content.length == 0
                || content.length > properties.getMaximumArtifactBytes()) {
            throw new ServiceException("交付文件内容类型或大小无效");
        }
        switch (type) {
            case "pptx" -> requireZipEntries(content, Set.of("[Content_Types].xml", "ppt/presentation.xml"));
            case "image_zip" -> requireZipEntries(content, Set.of("manifest.json"));
            case "jpg" -> {
                if (content.length < 4 || content[0] != (byte) 0xFF || content[1] != (byte) 0xD8
                        || content[content.length - 2] != (byte) 0xFF || content[content.length - 1] != (byte) 0xD9) {
                    throw new ServiceException("JPG 文件签名无效");
                }
            }
            case "csv" -> {
                if (content.length < 4 || content[0] != (byte) 0xEF || content[1] != (byte) 0xBB
                        || content[2] != (byte) 0xBF) throw new ServiceException("CSV 必须使用 UTF-8 BOM");
            }
            default -> throw new ServiceException("交付类型无效");
        }
    }

    private static void requireZipEntries(byte[] content, Set<String> required) {
        Set<String> found = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(content),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (++entries > 500 || name.startsWith("/") || name.contains("..") || name.contains("\\")) {
                    throw new ServiceException("ZIP 包结构不安全");
                }
                found.add(name);
                zip.closeEntry();
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("ZIP 包结构无效");
        }
        if (!found.containsAll(required)) throw new ServiceException("ZIP 包缺少必要结构");
    }

    private static void validateArtifactMetadata(String type, DeliveryArtifact artifact) {
        if (artifact == null || artifact.objectKey() == null || artifact.sha256() == null
                || !artifact.sha256().matches("[a-f0-9]{64}") || artifact.byteSize() <= 0
                || !CONTENT_TYPES.get(type).equals(artifact.contentType())) {
            throw new ServiceException("交付文件元数据无效");
        }
    }

    private long accessibleQuote(String quoteId) {
        long id = FashionId.parse(quoteId).value();
        quotes.requireAccessible(id);
        return id;
    }

    private DeliveryQuoteSnapshot requireConfirmed(long quoteId) {
        return repository.findConfirmedSnapshot(quoteId)
                .orElseThrow(() -> new ServiceException("只有内容完整的已确认报价可以交付", 409));
    }

    private DeliveryFileTask requireOwned(String fileId, long quoteId) {
        DeliveryFileTask task = repository.findById(FashionId.parse(fileId).value())
                .orElseThrow(() -> new ServiceException("导出任务不存在", 404));
        if (task.quoteId() != quoteId) throw new ServiceException("导出任务不属于当前报价", 403);
        return task;
    }

    private static String requestKey(DeliveryQuoteSnapshot quote, String type, String purpose, String renderer) {
        return FashionHashing.sha256((quote.quoteId() + "\n" + quote.contentHash() + "\n" + type + "\n"
                + purpose + "\n" + renderer).getBytes(StandardCharsets.UTF_8));
    }

    private static String safeError(Exception exception) {
        String message = exception instanceof ServiceException ? exception.getMessage() : "交付文件生成失败";
        if (message == null || message.isBlank()) message = "交付文件生成失败";
        message = message.replaceAll("(?i)(secret|token|password|cookie|signature)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return message.substring(0, Math.min(message.length(), 1000));
    }

    private static Duration nonNegative(Instant start, Instant end) {
        Duration value = Duration.between(start, end);
        return value.isNegative() ? Duration.ZERO : value;
    }

    private static String extensionType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pptx")) return "pptx";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".zip")) return "image_zip";
        return "jpg";
    }

    private static ServiceException conflict() {
        return new ServiceException("导出任务已被其他操作修改，请刷新", 409);
    }
}
