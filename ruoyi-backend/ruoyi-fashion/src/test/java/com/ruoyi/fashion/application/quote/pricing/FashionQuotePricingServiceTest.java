package com.ruoyi.fashion.application.quote.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCommand.Line;
import com.ruoyi.fashion.application.quote.pricing.port.FashionQuotePricingRepository;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.persistence.FashionCatalogWriteLock;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class FashionQuotePricingServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private final FashionQuotePricingRepository repository = mock(FashionQuotePricingRepository.class);
    private final FashionQuoteDraftService quotes = mock(FashionQuoteDraftService.class);
    private final FashionCatalogWriteLock lock = mock(FashionCatalogWriteLock.class);
    private final ISysConfigService config = mock(ISysConfigService.class);
    private final FashionTimeSource time = mock(FashionTimeSource.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final AtomicReference<QuotePricingState> state = new AtomicReference<>();
    private FashionQuotePricingService service;

    @BeforeEach
    void setUp() {
        state.set(draft(null, 3));
        when(repository.find(100L, false)).thenAnswer(invocation -> Optional.of(state.get()));
        when(repository.find(100L, true)).thenAnswer(invocation -> Optional.of(state.get()));
        when(config.selectConfigByKey("fashion.stock.freshnessHours")).thenReturn("24");
        when(time.now()).thenReturn(NOW);
        when(lock.executeLocked(anyInt(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Supplier<Object> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(transaction.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction((TransactionStatus) null);
        });
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null); return null;
        }).when(transaction).executeWithoutResult(any());
        service = new FashionQuotePricingService(repository, quotes, new QuotePricingCalculator(), lock, config,
                time, new ObjectMapper(), transaction);
    }

    @Test
    void workspaceAndSaveUseJavaDecimalResultAndPersistOnlyCorrectAmountLayer() {
        QuotePricingWorkspace initial = service.get("100");
        assertThat(initial.totalAmount()).isEqualTo("24050.00");
        QuotePricingCommand command = command("alternatives", "5.00", 3);
        AtomicReference<QuotePricingWrite> captured = new AtomicReference<>();
        when(repository.saveDraft(any())).thenAnswer(invocation -> { captured.set(invocation.getArgument(0)); return true; });

        service.save("100", command, 9);

        assertThat(captured.get().totalAmount()).isNull();
        assertThat(captured.get().combos().get(0).totalAmount()).isEqualByComparingTo("24050.00");
    }

    @Test
    void requesterCannotApproveTheOwnExceptionRequest() {
        QuotePricingWorkspace workspace = service.get("100");
        ObjectNode requested = new ObjectMapper().createObjectNode();
        requested.put("status", "requested"); requested.put("input_hash", workspace.inputHash());
        requested.put("requested_by", "9"); requested.put("expires_at", NOW.plusSeconds(3600).toString());
        state.set(draft(requested, 4));

        assertThatThrownBy(() -> service.approve("100",
                new QuoteApprovalCommand(workspace.inputHash(), "同意", 4), 9))
                .isInstanceOf(ServiceException.class).satisfies(error ->
                        assertThat(((ServiceException) error).getCode()).isEqualTo(403));
        verify(repository, never()).updateApproval(anyLong(), any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void anyCommercialContentChangeInvalidatesAnExistingApproval() {
        QuotePricingWorkspace preview = service.get("100");
        QuotePricingState approved = withDiscountAndApproval(draft(null, 3), "11.00", approved(preview.inputHash()));
        state.set(approved);
        QuotePricingWorkspace current = service.get("100");
        ObjectNode approval = approved(current.inputHash());
        state.set(withApprovalAndPublicNote(approved, approval, null));
        assertThat(service.get("100").approvalValid()).isTrue();

        state.set(withApprovalAndPublicNote(approved, approval, "新商务备注"));

        assertThat(service.get("100").approvalValid()).isFalse();
    }

    @Test
    void confirmationRunsInsideCatalogLockAndFreezesTheSameThreeLayerHash() {
        QuotePricingWorkspace preview = service.get("100");
        when(repository.confirm(any(), anyLong(), any())).thenAnswer(invocation -> {
            QuotePricingWrite write = invocation.getArgument(0);
            state.set(confirmed(write.contentHash(), 4));
            return true;
        });

        QuotePricingWorkspace confirmed = service.confirm("100",
                new QuoteConfirmCommand(preview.inputHash(), 3), 9);

        assertThat(confirmed.status()).isEqualTo("confirmed");
        assertThat(confirmed.inputHash()).isEqualTo(preview.inputHash());
        assertThat(confirmed.totalAmount()).isEqualTo("24050.00");
        verify(lock).executeLocked(anyInt(), any());
        verify(repository).confirm(any(), anyLong(), any());
    }

    @Test
    void excessiveDiscountNeedsApprovalAndCannotBeConfirmedWithoutIt() {
        state.set(withDiscountAndApproval(draft(null, 3), "11.00", null));
        QuotePricingWorkspace preview = service.get("100");

        assertThat(preview.approvalRequired()).isTrue();
        assertThatThrownBy(() -> service.confirm("100", new QuoteConfirmCommand(preview.inputHash(), 3), 9))
                .isInstanceOf(ServiceException.class).hasMessageContaining("需要有效负责人例外");
    }

    @Test
    void approvalRequestRecordsTheRevisionAndExactAllowedCommercialScope() {
        state.set(withDiscountAndApproval(draft(null, 3), "11.00", null));
        QuotePricingWorkspace preview = service.get("100");
        AtomicReference<tools.jackson.databind.JsonNode> captured = new AtomicReference<>();
        when(repository.updateApproval(anyLong(), any(), any(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> { captured.set(invocation.getArgument(1)); return true; });

        service.requestApproval("100", new QuoteApprovalCommand(preview.inputHash(), "团购专项政策", 3), 8);

        assertThat(captured.get().path("quote_no").asText()).isEqualTo("FQ-100");
        assertThat(captured.get().path("quote_version").asInt()).isEqualTo(1);
        assertThat(captured.get().path("scope").path("discount_rate").asText()).isEqualTo("11.00");
        assertThat(captured.get().path("expires_at").asText()).isNotBlank();
    }

    @Test
    void approvalCanNeverHideAllocationFailure() {
        QuotePricingState excessive = withDiscountAndApproval(draft(null, 3), "11.00", null);
        state.set(excessive);
        QuotePricingWorkspace preview = service.get("100");
        QuotePricingState approvedState = withDiscountAndApproval(excessive, "11.00", approved(preview.inputHash()));
        QuotePricingState broken = withQty(approvedState, 90);
        state.set(broken);
        QuotePricingWorkspace blocked = service.get("100");
        assertThat(blocked.issues()).anyMatch(issue -> issue.code().equals("ALLOCATION_MISMATCH"));
        assertThatThrownBy(() -> service.confirm("100", new QuoteConfirmCommand(blocked.inputHash(), 3), 9))
                .isInstanceOf(ServiceException.class).hasMessageContaining("阻断项");
    }

    @Test
    void imagePermissionIsPartOfTheConfirmationFacts() {
        state.set(withImageAllowed(draft(null, 3), false));

        QuotePricingWorkspace blocked = service.get("100");

        assertThat(blocked.issues()).anyMatch(issue -> issue.code().equals("IMAGE_PERMISSION_DENIED"));
        assertThatThrownBy(() -> service.confirm("100", new QuoteConfirmCommand(blocked.inputHash(), 3), 9))
                .isInstanceOf(ServiceException.class).hasMessageContaining("方案使用许可");
    }

    @Test
    void anInvalidAdoptedImageCannotEnterTheConfirmedQuote() {
        state.set(withSelectedImageValidity(draft(null, 3), false));

        QuotePricingWorkspace blocked = service.get("100");

        assertThat(blocked.issues()).anyMatch(issue -> issue.code().equals("SELECTED_IMAGE_INVALID"));
        assertThatThrownBy(() -> service.confirm("100", new QuoteConfirmCommand(blocked.inputHash(), 3), 9))
                .isInstanceOf(ServiceException.class).hasMessageContaining("采用图已失效");
    }

    @Test
    void changingTheQuoteQuantityCannotLeaveAnOldAllocationConfirmable() {
        state.set(withRequestedQty(draft(null, 3), 120));

        QuotePricingWorkspace blocked = service.get("100");

        assertThat(blocked.issues()).anyMatch(issue -> issue.code().equals("QUOTE_QTY_MISMATCH"));
    }

    private QuotePricingCommand command(String mode, String discount, long version) {
        return new QuotePricingCommand(mode, "included", null, true, "percent", new BigDecimal(discount),
                BigDecimal.ZERO, new BigDecimal("300.00"), 7, null, List.of("200"),
                List.of(new Line("301", 100, new BigDecimal("80.00")),
                        new Line("302", 100, new BigDecimal("60.00")),
                        new Line("303", 100, new BigDecimal("20.00")),
                        new Line("304", 100, new BigDecimal("90.00"))), version);
    }

    private QuotePricingState draft(ObjectNode approval, long version) {
        List<QuotePricingState.Line> lines = List.of(fact(301, "top", 1, "80.00", 100),
                fact(302, "pants", 2, "60.00", 100), fact(303, "hat", 3, "20.00", 130),
                fact(304, "shoes", 4, "90.00", 100));
        QuotePricingState.Combo combo = new QuotePricingState.Combo(200, "C-1", "四品类", 4, 100, true, true,
                null, null, "a".repeat(64), null, true, null, null, null, null, null, 2, lines);
        return new QuotePricingState(100, "FQ-100", 1, 10, "客户", 100, "combined", "MAIN", "CNY",
                "included", null, true, "percent", new BigDecimal("5.00"), BigDecimal.ZERO,
                new BigDecimal("300.00"), null, null, null, null, approval, 7, null, null, null,
                "draft", version, List.of(combo));
    }

    private QuotePricingState confirmed(String hash, long version) {
        QuotePricingState base = draft(null, version);
        QuotePricingState.Combo c = base.combos().get(0);
        QuotePricingState.Combo frozen = new QuotePricingState.Combo(c.id(), c.comboNo(), c.name(), c.categoryCount(), c.setQty(),
                c.selected(), true, c.selectedImageId(), c.selectedImageNo(), c.visualHash(),
                c.selectedImageHash(), c.selectedImageValid(),
                new BigDecimal("25000.00"), new BigDecimal("1250.00"), new BigDecimal("300.00"),
                BigDecimal.ZERO.setScale(2), new BigDecimal("24050.00"), c.rowVersion(), c.lines());
        return new QuotePricingState(base.quoteId(), base.quoteNo(), base.versionNo(), base.customerId(),
                base.customerName(), base.requestedQty(), base.mode(), base.warehouseCode(), base.currency(), base.taxMode(), base.taxRate(),
                base.feeTaxable(), base.discountType(), base.discountRate(), base.fixedDiscount(), base.freight(),
                new BigDecimal("25000.00"), new BigDecimal("1250.00"), BigDecimal.ZERO.setScale(2),
                new BigDecimal("24050.00"), null, 7, NOW.plus(7, java.time.temporal.ChronoUnit.DAYS), null,
                hash, "confirmed", version, List.of(frozen));
    }

    private QuotePricingState.Line fact(long id, String slot, long product, String price, int stock) {
        BigDecimal money = new BigDecimal(price);
        return new QuotePricingState.Line(id, slot, product, "SKU-" + product, "商品" + product, slot,
                "黑色", "M", "件", 100, money, money, money.multiply(new BigDecimal("100")), 10L,
                money, "CNY", "included", NOW.minusSeconds(3600), 10L, 1, "active",
                stock, NOW.minusSeconds(3600), 20L, stock, NOW.minusSeconds(3600), 20L, 1,
                "materials/" + product + ".png", "b".repeat(64), 1L,
                "materials/" + product + ".png", "b".repeat(64), true, 1, 1);
    }

    private QuotePricingState withQty(QuotePricingState source, int qty) {
        QuotePricingState.Combo c = source.combos().get(0);
        QuotePricingState.Line first = c.lines().get(0);
        QuotePricingState.Line changed = new QuotePricingState.Line(first.id(), first.slotCode(), first.productId(),
                first.skuCode(), first.productName(), first.categoryCode(), first.colorName(), first.sizeCode(),
                first.unit(), qty, first.frozenSourcePrice(), first.quotePrice(), first.amount(),
                first.frozenPriceBatchId(), first.currentPrice(), first.currentCurrency(), first.currentTaxMode(),
                first.currentPriceAsOf(), first.currentPriceBatchId(), first.currentProductRowVersion(),
                first.currentStatus(), first.frozenStockQty(), first.frozenStockAsOf(), first.frozenStockBatchId(),
                first.currentStockQty(), first.currentStockAsOf(), first.currentStockBatchId(),
                first.currentStockRowVersion(), first.frozenImageKey(), first.frozenImageHash(),
                first.frozenImageVersion(), first.currentImageKey(), first.currentImageHash(),
                first.currentImageAllowed(), first.currentVisualVersion(), first.rowVersion());
        List<QuotePricingState.Line> lines = new java.util.ArrayList<>(c.lines()); lines.set(0, changed);
        QuotePricingState.Combo combo = new QuotePricingState.Combo(c.id(), c.comboNo(), c.name(), c.categoryCount(), c.setQty(),
                c.selected(), c.allocationConfirmed(), c.selectedImageId(), c.selectedImageNo(), c.visualHash(),
                c.selectedImageHash(), c.selectedImageValid(),
                c.subtotal(), c.discountAmount(), c.freight(), c.taxAmount(), c.totalAmount(), c.rowVersion(), lines);
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), source.requestedQty(), source.mode(), source.warehouseCode(), source.currency(), source.taxMode(),
                source.taxRate(), source.feeTaxable(), source.discountType(), source.discountRate(),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), source.approval(), source.validDays(), source.validUntil(), source.publicNote(),
                source.contentHash(), source.status(), source.rowVersion(), List.of(combo));
    }

    private QuotePricingState withDiscountAndApproval(
            QuotePricingState source, String discount, ObjectNode approval) {
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), source.requestedQty(), source.mode(), source.warehouseCode(), source.currency(), source.taxMode(),
                source.taxRate(), source.feeTaxable(), source.discountType(), new BigDecimal(discount),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), approval, source.validDays(), source.validUntil(), source.publicNote(),
                source.contentHash(), source.status(), source.rowVersion(), source.combos());
    }

    private QuotePricingState withImageAllowed(QuotePricingState source, boolean allowed) {
        QuotePricingState.Combo combo = source.combos().get(0);
        List<QuotePricingState.Line> lines = combo.lines().stream().map(line -> new QuotePricingState.Line(
                line.id(), line.slotCode(), line.productId(), line.skuCode(), line.productName(), line.categoryCode(),
                line.colorName(), line.sizeCode(), line.unit(), line.qty(), line.frozenSourcePrice(), line.quotePrice(),
                line.amount(), line.frozenPriceBatchId(), line.currentPrice(), line.currentCurrency(),
                line.currentTaxMode(), line.currentPriceAsOf(), line.currentPriceBatchId(),
                line.currentProductRowVersion(), line.currentStatus(), line.frozenStockQty(), line.frozenStockAsOf(),
                line.frozenStockBatchId(), line.currentStockQty(), line.currentStockAsOf(),
                line.currentStockBatchId(), line.currentStockRowVersion(), line.frozenImageKey(),
                line.frozenImageHash(), line.frozenImageVersion(), line.currentImageKey(), line.currentImageHash(),
                allowed, line.currentVisualVersion(), line.rowVersion())).toList();
        QuotePricingState.Combo changed = new QuotePricingState.Combo(combo.id(), combo.comboNo(), combo.name(),
                combo.categoryCount(), combo.setQty(), combo.selected(), combo.allocationConfirmed(),
                combo.selectedImageId(), combo.selectedImageNo(), combo.visualHash(), combo.selectedImageHash(),
                combo.selectedImageValid(), combo.subtotal(),
                combo.discountAmount(), combo.freight(), combo.taxAmount(), combo.totalAmount(), combo.rowVersion(), lines);
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), source.requestedQty(), source.mode(), source.warehouseCode(), source.currency(), source.taxMode(),
                source.taxRate(), source.feeTaxable(), source.discountType(), source.discountRate(),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), source.approval(), source.validDays(), source.validUntil(), source.publicNote(),
                source.contentHash(), source.status(), source.rowVersion(), List.of(changed));
    }

    private QuotePricingState withApprovalAndPublicNote(
            QuotePricingState source, ObjectNode approval, String publicNote) {
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), source.requestedQty(), source.mode(), source.warehouseCode(), source.currency(), source.taxMode(),
                source.taxRate(), source.feeTaxable(), source.discountType(), source.discountRate(),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), approval, source.validDays(), source.validUntil(), publicNote,
                source.contentHash(), source.status(), source.rowVersion(), source.combos());
    }

    private QuotePricingState withSelectedImageValidity(QuotePricingState source, boolean valid) {
        QuotePricingState.Combo combo = source.combos().get(0);
        QuotePricingState.Combo changed = new QuotePricingState.Combo(combo.id(), combo.comboNo(), combo.name(),
                combo.categoryCount(), combo.setQty(), combo.selected(), combo.allocationConfirmed(), 999L, 1,
                combo.visualHash(), "c".repeat(64), valid, combo.subtotal(), combo.discountAmount(), combo.freight(),
                combo.taxAmount(), combo.totalAmount(), combo.rowVersion(), combo.lines());
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), source.requestedQty(), source.mode(), source.warehouseCode(), source.currency(),
                source.taxMode(), source.taxRate(), source.feeTaxable(), source.discountType(), source.discountRate(),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), source.approval(), source.validDays(), source.validUntil(), source.publicNote(),
                source.contentHash(), source.status(), source.rowVersion(), List.of(changed));
    }

    private QuotePricingState withRequestedQty(QuotePricingState source, int requestedQty) {
        return new QuotePricingState(source.quoteId(), source.quoteNo(), source.versionNo(), source.customerId(),
                source.customerName(), requestedQty, source.mode(), source.warehouseCode(), source.currency(),
                source.taxMode(), source.taxRate(), source.feeTaxable(), source.discountType(), source.discountRate(),
                source.fixedDiscount(), source.freight(), source.subtotal(), source.discountAmount(), source.taxAmount(),
                source.totalAmount(), source.approval(), source.validDays(), source.validUntil(), source.publicNote(),
                source.contentHash(), source.status(), source.rowVersion(), source.combos());
    }

    private ObjectNode approved(String hash) {
        ObjectNode node = new ObjectMapper().createObjectNode(); node.put("status", "approved");
        node.put("input_hash", hash); node.put("requested_by", "8"); node.put("approved_by", "9");
        node.put("expires_at", NOW.plusSeconds(3600).toString()); return node;
    }
}
