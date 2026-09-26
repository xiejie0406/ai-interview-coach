package com.ruoyi.fashion.application.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.image.port.FashionImageProviderPort;
import com.ruoyi.fashion.application.image.port.FashionQuoteImageRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.application.selection.SelectionComboView;
import com.ruoyi.fashion.application.selection.port.FashionSelectionRepository;
import com.ruoyi.fashion.configuration.FashionImageProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import io.opentelemetry.api.OpenTelemetry;

class FashionQuoteImageServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FashionQuoteImageRepository repository = mock(FashionQuoteImageRepository.class);
    private final FashionSelectionRepository selections = mock(FashionSelectionRepository.class);
    private final FashionQuoteDraftService quotes = mock(FashionQuoteDraftService.class);
    private final FashionImageProviderPort provider = mock(FashionImageProviderPort.class);
    private final ISysConfigService config = mock(ISysConfigService.class);
    private final FashionTimeSource time = mock(FashionTimeSource.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final AtomicReference<QuoteImageTask> stored = new AtomicReference<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private FashionImageProperties properties;
    private FashionQuoteImageService service;
    private ObjectNode slot;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        when(time.now()).thenReturn(now);
        when(config.selectConfigByKey("fashion.image.maxResults")).thenReturn("4");
        when(config.selectConfigByKey("fashion.ai.monthlyBudgetCny")).thenReturn("10.00");
        when(repository.settledCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(repository.committedCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(quotes.requireAccessible(100L)).thenReturn(quote());
        SelectionComboView combo = new SelectionComboView("200", "100", "C-200", "四品类", 4, 100,
                true, 0, "测试", List.of(), "a".repeat(64), 4, List.of());
        when(selections.findCombo(100L, 200L)).thenReturn(Optional.of(combo));
        when(selections.findByQuoteId(100L)).thenReturn(List.of(combo));
        slot = mapper.createObjectNode(); slot.put("slot_code", "SLOT-1");
        slot.putArray("products").addObject().put("product_id", "300").put("visual_version", 2)
                .put("category_code", "上衣").put("style_code", "ST-1").put("color_code", "WHITE");
        slot.put("image_key", "materials/a/original.png"); slot.put("image_hash", "b".repeat(64));
        slot.putObject("permission_snapshot").put("allow_ai", true).put("allow_proposal", true)
                .put("allow_ecommerce", true);
        when(repository.currentInputs(200L)).thenReturn(mapper.createArrayNode().add(slot));
        when(repository.findByRequestKey(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get())
                .filter(task -> task.requestKey().equals(invocation.getArgument(0))));
        when(repository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(stored.get())
                .filter(task -> task.id() == (long) invocation.getArgument(0)));
        when(repository.findByQuoteId(100L)).thenAnswer(invocation ->
                stored.get() == null ? List.of() : List.of(stored.get()));
        doAnswer(invocation -> { stored.set(invocation.getArgument(0)); return null; })
                .when(repository).insert(any(), anyLong(), any());
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0); callback.accept(null); return null;
        }).when(transaction).executeWithoutResult(any());
        when(transaction.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        properties = new FashionImageProperties();
        FashionObjectStoragePort storage = new FashionObjectStoragePort() {
            @Override public StoredFashionObject putIfAbsent(String key, byte[] content, String type) {
                return new StoredFashionObject(key, FashionHashing.sha256(content), content.length, type);
            }
            @Override public byte[] read(String key) { return new byte[] {1}; }
        };
        FashionIdGenerator generator = ids::incrementAndGet;
        service = new FashionQuoteImageService(repository, selections, quotes, storage, provider, properties,
                config, generator, time, mapper, transaction, new FashionTelemetry(OpenTelemetry.noop()));
    }

    @Test
    void sampleTaskIsIdempotentAndNeverPretendsToUseProviderOrBilling() {
        QuoteImageView first = service.create("100", command("sample", "image:test:sample"), 9);
        QuoteImageView repeated = service.create("100", command("sample", "image:test:sample"), 9);

        assertEquals(first.id(), repeated.id());
        assertEquals("体验样例", first.sourceLabel());
        assertEquals("not_applicable", first.billingStatus());
        assertEquals("queued", first.status());
        assertEquals(null, first.providerTaskId());
    }

    @Test
    void reusedRequestKeyMustDescribeExactlyTheSameIntent() {
        service.create("100", command("sample", "image:test:intent"), 9);
        QuoteImageCreateCommand changed = new QuoteImageCreateCommand("200", "model", "sample", 1,
                mapper.createObjectNode().put("aspectRatio", "1:1").put("promptVersion", "2.0"),
                "image:test:intent", 3, 4, "a".repeat(64));

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.create("100", changed, 9));

        assertEquals(409, conflict.getCode());
        assertTrue(conflict.getMessage().contains("不同图片任务"));
    }

    @Test
    void providerStaysFailClosedAndInsufficientBudgetNeverCreatesTask() {
        ServiceException disabled = assertThrows(ServiceException.class,
                () -> service.create("100", command("provider", "image:test:disabled"), 9));
        assertTrue(disabled.getMessage().contains("未启用"));

        properties.setProviderEnabled(true);
        when(provider.available()).thenReturn(true);
        when(config.selectConfigByKey("fashion.ai.monthlyBudgetCny")).thenReturn("0.00");
        ServiceException noBudget = assertThrows(ServiceException.class,
                () -> service.create("100", command("provider", "image:test:budget"), 9));
        assertTrue(noBudget.getMessage().contains("预算不足"));
        assertEquals(null, stored.get());
    }

    @Test
    void viewingNeverConfirmsAndPassRequiresEveryReviewCheck() {
        service.create("100", command("sample", "image:test:review"), 9);
        stored.set(withResults(stored.get(), successfulResult(), "success", 2));
        assertEquals("pending_review", service.workspace("100").tasks().get(0).displayStatus());

        ServiceException missing = assertThrows(ServiceException.class, () -> service.review("100",
                Long.toString(stored.get().id()), new QuoteImageReviewCommand(1, "pass", List.of("style"), null, 2), 9));
        assertTrue(missing.getMessage().contains("逐项确认"));
    }

    @Test
    void reviewRejectsStaleClientRowVersionBeforeMutatingHistory() {
        service.create("100", command("sample", "image:test:review-version"), 9);
        stored.set(withResults(stored.get(), successfulResult(), "success", 3));

        ServiceException conflict = assertThrows(ServiceException.class, () -> service.review("100",
                Long.toString(stored.get().id()), new QuoteImageReviewCommand(1, "reject", List.of(),
                        "low_quality", 2), 9));

        assertEquals(409, conflict.getCode());
        assertTrue(conflict.getMessage().contains("版本已变化"));
        verify(repository, never()).updateReview(anyLong(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void staleVisualInputBlocksAdoptionAndMarksTaskForReview() {
        service.create("100", command("sample", "image:test:stale"), 9);
        ObjectNode result = successfulResult();
        result.withArray("reviews").addObject().put("decision", "pass").put("reviewer", "9")
                .put("time", "2026-09-13T00:00:00Z").putNull("reason")
                .set("checklist", mapper.valueToTree(List.of("slot_count", "style", "color", "logo",
                        "completeness", "pose", "quality")));
        result.withArray("reviews").get(0).deepCopy();
        ((ObjectNode) result.withArray("reviews").get(0)).put("input_hash", stored.get().inputHash());
        stored.set(withResults(stored.get(), result, "success", 2));
        ObjectNode changed = slot.deepCopy(); changed.put("image_hash", "c".repeat(64));
        when(repository.currentInputs(200L)).thenReturn(mapper.createArrayNode().add(changed));
        when(repository.markStale(anyLong(), anyLong(), anyLong(), any())).thenReturn(true);

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.adopt("100", Long.toString(stored.get().id()), 1, 2, 9));
        assertEquals(409, conflict.getCode());
    }

    @Test
    void cancelledQueuedTaskBecomesTerminalWithoutProviderCall() {
        service.create("100", command("sample", "image:test:cancel"), 9);
        when(repository.requestCancel(stored.get().id(), 1, 9, Instant.parse("2026-09-13T00:00:00Z")))
                .thenAnswer(invocation -> { stored.set(copy(stored.get(), "cancelled", stored.get().results(), 2)); return true; });

        QuoteImageView cancelled = service.cancel("100", Long.toString(stored.get().id()), 1, 9);

        assertEquals("cancelled", cancelled.status());
        assertFalse(cancelled.stale());
    }

    @Test
    void providerPartialResultIsPreservedAndSettledThroughFencedCompletion() {
        properties.setProviderEnabled(true);
        when(provider.available()).thenReturn(true);
        service.create("100", command("provider", "image:test:partial"), 9);
        QuoteImageTask claimed = copy(stored.get(), "queued", stored.get().results(), 2);
        when(repository.claimNext(eq("worker-1"), any(), any())).thenReturn(Optional.of(claimed));
        ArrayNode results = mapper.createArrayNode().add(successfulResult());
        when(provider.submit(eq("image:test:partial"), eq("model"), any(), any(), eq(2)))
                .thenReturn(new FashionImageProviderPort.ProviderResult("partial", "fake-provider", "job-1",
                        results, new BigDecimal("1.50"), "CNY", null, null, false));
        when(repository.updateExecution(anyLong(), anyLong(), any(), any(), nullable(String.class),
                nullable(String.class), eq(0), nullable(Instant.class), nullable(BigDecimal.class), any(), any(),
                nullable(String.class), nullable(Instant.class), any())).thenReturn(true);

        assertTrue(service.processNext("worker-1"));

        verify(repository).updateExecution(eq(claimed.id()), eq(2L), eq("partial"), eq(results),
                eq("fake-provider"), eq("job-1"), eq(0), nullable(Instant.class),
                eq(new BigDecimal("1.50")), eq("settled"), any(), nullable(String.class), any(), any());
    }

    @Test
    void unknownTaskQueriesOriginalKeyInsteadOfBlindResubmitAndLateCancelCannotAdopt() {
        properties.setProviderEnabled(true);
        when(provider.available()).thenReturn(true);
        service.create("100", command("provider", "image:test:unknown"), 9);
        QuoteImageTask unknown = copy(stored.get(), "unknown", stored.get().results(), 5);
        when(repository.claimNext(eq("worker-2"), any(), any())).thenReturn(Optional.of(unknown));
        when(provider.query("image:test:unknown")).thenReturn(new FashionImageProviderPort.ProviderResult(
                "running", "fake-provider", "job-late", mapper.createArrayNode(), null, "CNY", null, null, false));
        when(repository.updateExecution(anyLong(), anyLong(), any(), any(), nullable(String.class),
                nullable(String.class), any(Integer.class), nullable(Instant.class), nullable(BigDecimal.class), any(),
                any(), nullable(String.class), nullable(Instant.class), any())).thenReturn(true);

        assertTrue(service.processNext("worker-2"));
        verify(provider).query("image:test:unknown");
        verify(provider, never()).submit(any(), any(), any(), any(), any(Integer.class));

        QuoteImageTask cancelled = new QuoteImageTask(unknown.id(), unknown.quoteId(), unknown.comboId(), null, null,
                unknown.imageType(), unknown.sourceMode(), unknown.inputHash(), unknown.inputs(), unknown.parameters(),
                unknown.requestedCount(), resultsWithLateSuccess(), "fake-provider", "job-late", unknown.requestKey(),
                "cancel_requested", false, 0, null, null, unknown.estimatedCost(), null, "CNY", "reserved",
                unknown.billingEvents(), null, null, unknown.createTime(), false, null, 6);
        when(repository.claimNext(eq("worker-3"), any(), any())).thenReturn(Optional.of(cancelled));
        assertTrue(service.processNext("worker-3"));
        verify(provider).cancel("job-late");
        verify(repository).updateExecution(eq(cancelled.id()), eq(6L), eq("cancelled"), any(), eq("fake-provider"),
                eq("job-late"), eq(0), nullable(Instant.class), nullable(BigDecimal.class), eq("unknown"), any(),
                nullable(String.class), any(), any());
    }

    private QuoteImageCreateCommand command(String mode, String key) {
        return new QuoteImageCreateCommand("200", "model", mode, 2,
                mapper.createObjectNode().put("aspectRatio", "3:4").put("promptVersion", "1.0"),
                key, 3, 4, "a".repeat(64));
    }

    private ObjectNode successfulResult() {
        ObjectNode result = mapper.createObjectNode(); result.put("no", 1); result.put("status", "success");
        result.put("object_key", "quote-images/result.png"); result.put("sha256", "d".repeat(64));
        result.put("width", 768); result.put("height", 1024); result.putNull("error");
        result.put("allow_proposal", true); result.put("allow_ecommerce", false); result.putArray("reviews");
        return result;
    }

    private ArrayNode resultsWithLateSuccess() {
        return mapper.createArrayNode().add(successfulResult());
    }

    private QuoteImageTask withResults(QuoteImageTask task, ObjectNode result, String status, long version) {
        return copy(task, status, mapper.createArrayNode().add(result), version);
    }

    private QuoteImageTask copy(QuoteImageTask task, String status, JsonNode results, long version) {
        return new QuoteImageTask(task.id(), task.quoteId(), task.comboId(), task.aiRunId(), task.sourceImageId(),
                task.imageType(), task.sourceMode(), task.inputHash(), task.inputs(), task.parameters(),
                task.requestedCount(), results, task.providerCode(), task.providerTaskId(), task.requestKey(), status,
                task.stale(), task.retryCount(), task.nextRetryAt(), task.leaseUntil(), task.estimatedCost(),
                task.actualCost(), task.costCurrency(), task.billingStatus(), task.billingEvents(), task.errorMessage(),
                task.finishedAt(), task.createTime(), task.adopted(), task.adoptedResultNo(), version);
    }

    private FashionQuote quote() {
        ObjectNode empty = mapper.createObjectNode();
        return new FashionQuote(100, "FQ-100", 1, null, 10, "客户", "方案", 9, "需求", empty,
                true, 100, new BigDecimal("300.00"), "per_set", "alternatives", true, empty,
                "MAIN", "CNY", "included", empty, "draft", 9, Instant.parse("2026-09-12T00:00:00Z"),
                9, Instant.parse("2026-09-13T00:00:00Z"), 3);
    }
}
