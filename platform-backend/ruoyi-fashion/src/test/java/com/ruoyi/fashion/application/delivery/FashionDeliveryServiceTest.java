package com.ruoyi.fashion.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.configuration.FashionDeliveryProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class FashionDeliveryServiceTest {
    private final Instant now = Instant.parse("2026-09-13T02:00:00Z");
    private FakeRepository repository;
    private MemoryStorage storage;
    private FashionDeliveryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository(DeliveryTestFixtures.snapshot());
        storage = new MemoryStorage();
        FashionQuoteDraftService quotes = mock(FashionQuoteDraftService.class);
        when(quotes.requireAccessible(anyLong())).thenReturn(mock(FashionQuote.class));
        FashionIdGenerator ids = () -> 8001L;
        FashionTimeSource time = mock(FashionTimeSource.class);
        when(time.now()).thenReturn(now);
        FashionDeliveryProperties properties = new FashionDeliveryProperties();
        properties.setLeaseDuration(Duration.ofMinutes(2));
        TransactionTemplate transaction = immediateTransaction();
        FashionDeliveryRenderer renderer = new FashionDeliveryRenderer() {
            @Override public String fileType() { return "csv"; }
            @Override public List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot snapshot) {
                return List.of(new GeneratedDeliveryArtifact("safe.csv", "text/csv;charset=UTF-8",
                        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'}, null,
                        "quote-detail", List.of("SKU"), "frozen-quote", "confirmed"));
            }
        };
        service = new FashionDeliveryService(repository, quotes, storage, ids, time, properties,
                transaction, List.of(renderer), mock(FashionTelemetry.class));
    }

    @Test
    void requestIsIdempotentAndWorkerCommitsOnlyNonEmptyVerifiedArtifact() {
        DeliveryRequestCommand command = new DeliveryRequestCommand("csv", "customer", null);
        DeliveryFileView first = service.request("1001", command, 7);
        DeliveryFileView second = service.request("1001", command, 7);
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(repository.inserts).isEqualTo(1);

        assertThat(service.processNext("delivery-worker")).isTrue();
        DeliveryFileView completed = service.workspace("1001").files().get(0);
        assertThat(completed.status()).isEqualTo("success");
        assertThat(completed.files()).hasSize(1);
        assertThat(completed.files().get(0).byteSize()).isEqualTo(4);
        assertThat(storage.values).hasSize(1);
    }

    @Test
    void objectStorageFailureRecordsFailedAndNeverCreatesSuccessOrEmptyMetadata() {
        service.request("1001", new DeliveryRequestCommand("csv", "customer", null), 7);
        storage.fail = true;
        assertThat(service.processNext("delivery-worker")).isTrue();
        DeliveryFileView failed = service.workspace("1001").files().get(0);
        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.retryCount()).isEqualTo(1);
        assertThat(failed.files()).isEmpty();
        assertThat(failed.errorMessage()).doesNotContain("secret-value");
    }

    @Test
    void downloadRechecksDigestAndCountsOnlyAfterExplicitCompletion() {
        service.request("1001", new DeliveryRequestCommand("csv", "customer", null), 7);
        service.processNext("delivery-worker");
        DeliveryFileView task = service.workspace("1001").files().get(0);
        DeliveryDownload download = service.download("1001", task.id(), 1);
        assertThat(download.content()).hasSize(4);
        assertThat(repository.task.downloadCount()).isZero();
        service.markDownloaded(download, 7);
        assertThat(repository.task.downloadCount()).isEqualTo(1);

        storage.values.replaceAll((key, value) -> new byte[] {1, 2, 3});
        assertThatThrownBy(() -> service.download("1001", task.id(), 1))
                .isInstanceOf(ServiceException.class).hasMessageContaining("摘要");
    }

    @Test
    void rejectsUnsupportedRendererAndCrossQuoteFileLookup() {
        assertThatThrownBy(() -> service.request("1001",
                new DeliveryRequestCommand("pptx", "customer", "unknown"), 7))
                .isInstanceOf(ServiceException.class);
        service.request("1001", new DeliveryRequestCommand("csv", "customer", null), 7);
        assertThatThrownBy(() -> service.download("1002", "8001", 1))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不属于");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TransactionTemplate immediateTransaction() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(template.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback) invocation.getArgument(0)).doInTransaction(status));
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(status);
            return null;
        }).when(template).executeWithoutResult(any());
        return template;
    }

    private static final class MemoryStorage implements FashionObjectStoragePort {
        private final Map<String, byte[]> values = new LinkedHashMap<>();
        private boolean fail;

        @Override
        public StoredFashionObject putIfAbsent(String key, byte[] content, String type) {
            if (fail) throw new ServiceException("storage secret=secret-value unavailable");
            values.putIfAbsent(key, content.clone());
            return new StoredFashionObject(key, FashionHashing.sha256(content), content.length, type);
        }

        @Override
        public byte[] read(String key) {
            byte[] value = values.get(key);
            if (value == null) throw new ServiceException("missing");
            return value.clone();
        }
    }

    private static final class FakeRepository implements FashionDeliveryRepository {
        private final DeliveryQuoteSnapshot snapshot;
        private DeliveryFileTask task;
        private int inserts;

        private FakeRepository(DeliveryQuoteSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public Optional<DeliveryQuoteSnapshot> findConfirmedSnapshot(long quoteId) {
            return quoteId == snapshot.quoteId() ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public List<DeliveryFileTask> findByQuote(long quoteId) {
            return task != null && task.quoteId() == quoteId ? List.of(task) : List.of();
        }
        @Override public Optional<DeliveryFileTask> findById(long id) {
            return task != null && task.id() == id ? Optional.of(task) : Optional.empty();
        }
        @Override public Optional<DeliveryFileTask> findByRequestKey(String key) {
            return task != null && task.requestKey().equals(key) ? Optional.of(task) : Optional.empty();
        }
        @Override public boolean insert(DeliveryFileTask value) { task = value; inserts++; return true; }
        @Override public Optional<DeliveryFileTask> claimNext(Instant now, Instant leaseUntil) {
            if (task == null || !("queued".equals(task.status()) || "failed".equals(task.status()))) return Optional.empty();
            task = copy("running", task.artifacts(), task.retryCount(), null, leaseUntil, null,
                    task.lastDownloadAt(), task.downloadCount(), task.rowVersion() + 1);
            return Optional.of(task);
        }
        @Override public boolean complete(long id, long version, List<DeliveryArtifact> artifacts, long operator, Instant now) {
            if (task.id() != id || task.rowVersion() != version) return false;
            task = copy("success", artifacts, task.retryCount(), null, null, null,
                    task.lastDownloadAt(), task.downloadCount(), task.rowVersion() + 1); return true;
        }
        @Override public boolean fail(long id, long version, int retry, Instant next, String error, long operator, Instant now) {
            if (task.id() != id || task.rowVersion() != version) return false;
            task = copy("failed", task.artifacts(), retry, next, null, error,
                    task.lastDownloadAt(), task.downloadCount(), task.rowVersion() + 1); return true;
        }
        @Override public boolean requeue(long id, long version, long operator, Instant now) { return false; }
        @Override public boolean markDownloaded(long id, long version, long operator, Instant now) {
            task = copy(task.status(), task.artifacts(), task.retryCount(), task.nextRetryAt(), task.leaseUntil(),
                    task.errorMessage(), now, task.downloadCount() + 1, task.rowVersion() + 1); return true;
        }
        @Override public boolean extendRetention(long id, long version, Instant until, long operator, Instant now) { return false; }

        private DeliveryFileTask copy(String status, List<DeliveryArtifact> artifacts, int retry,
                Instant next, Instant lease, String error, Instant downloaded, int downloads, long version) {
            return new DeliveryFileTask(task.id(), task.quoteId(), task.quoteNo(), task.versionNo(), task.quoteTitle(),
                    task.fileType(), task.purpose(), task.quoteHash(), task.rendererVersion(), task.requestKey(), status,
                    List.copyOf(artifacts), retry, next, lease, error, downloaded, downloads, task.createBy(),
                    task.createTime(), version);
        }
    }
}
