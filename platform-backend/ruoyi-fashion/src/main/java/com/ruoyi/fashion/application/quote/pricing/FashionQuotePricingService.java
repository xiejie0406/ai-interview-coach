package com.ruoyi.fashion.application.quote.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Calculation;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Combo;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.ComboResult;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Issue;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Line;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Request;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingWorkspace.CalculatedCombo;
import com.ruoyi.fashion.application.quote.pricing.port.FashionQuotePricingRepository;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.persistence.FashionCatalogWriteLock;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 报价草稿、例外和确认的事务编排；所有金额由 QuotePricingCalculator 计算。 */
@Service
public class FashionQuotePricingService {
    private final FashionQuotePricingRepository repository;
    private final FashionQuoteDraftService quotes;
    private final QuotePricingCalculator calculator;
    private final FashionCatalogWriteLock catalogWriteLock;
    private final ISysConfigService config;
    private final FashionTimeSource time;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public FashionQuotePricingService(FashionQuotePricingRepository repository, FashionQuoteDraftService quotes,
            QuotePricingCalculator calculator, FashionCatalogWriteLock catalogWriteLock, ISysConfigService config,
            FashionTimeSource time, ObjectMapper objectMapper,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction) {
        this.repository = repository; this.quotes = quotes; this.calculator = calculator;
        this.catalogWriteLock = catalogWriteLock; this.config = config; this.time = time;
        this.objectMapper = objectMapper; this.transaction = transaction;
    }

    public QuotePricingWorkspace get(String quoteId) {
        long id = accessibleId(quoteId);
        return workspace(requireState(id, false), null);
    }

    public QuotePricingWorkspace save(String quoteId, QuotePricingCommand command, long operatorId) {
        long id = accessibleId(quoteId);
        QuotePricingState state = requireState(id, false);
        requireDraftVersion(state, command == null ? -1 : command.rowVersion());
        validateTerms(command);
        Prepared prepared = prepare(state, command);
        QuotePricingWrite write = write(state, command, prepared, null, operatorId, time.now());
        transaction.executeWithoutResult(status -> {
            if (!repository.saveDraft(write)) throw conflict();
        });
        return get(quoteId);
    }

    public QuotePricingWorkspace requestApproval(String quoteId, QuoteApprovalCommand command, long operatorId) {
        long id = accessibleId(quoteId);
        QuotePricingState state = requireState(id, false);
        requireDraftVersion(state, command == null ? -1 : command.rowVersion());
        Prepared prepared = prepare(state, storedCommand(state));
        validateApprovalCommand(command, prepared.inputHash());
        if (!prepared.calculation().approvalRequired()) throw new ServiceException("当前报价不需要负责人例外");
        Instant now = time.now();
        ObjectNode approval = objectMapper.createObjectNode();
        approval.put("schema_version", "1.0"); approval.put("status", "requested");
        approval.put("quote_no", state.quoteNo()); approval.put("quote_version", state.versionNo());
        approval.put("input_hash", prepared.inputHash()); approval.put("requested_by", Long.toString(operatorId));
        approval.put("requested_at", now.toString()); approval.put("expires_at", now.plus(24, ChronoUnit.HOURS).toString());
        approval.put("reason", command.reason().trim()); approval.putNull("approved_by"); approval.putNull("approved_at");
        ObjectNode scope = approval.putObject("scope");
        scope.put("discount_type", prepared.request().discountType());
        scope.put("discount_rate", prepared.request().discountRate().toPlainString());
        scope.put("fixed_discount", prepared.request().fixedDiscount().toPlainString());
        ArrayNode adjustments = scope.putArray("unit_price_adjustments");
        prepared.request().combos().stream().filter(Combo::selected).flatMap(combo -> combo.lines().stream())
                .filter(line -> line.sourcePrice() == null || line.sourcePrice().compareTo(line.unitPrice()) != 0)
                .forEach(line -> {
                    ObjectNode item = adjustments.addObject();
                    item.put("detail_id", Long.toString(line.id())); item.put("sku_code", line.skuCode());
                    if (line.sourcePrice() == null) item.putNull("original_price");
                    else item.put("original_price", line.sourcePrice().toPlainString());
                    item.put("allowed_price", line.unitPrice().toPlainString());
                });
        if (!repository.updateApproval(id, approval, prepared.inputHash(), state.rowVersion(), operatorId, now)) {
            throw conflict();
        }
        return get(quoteId);
    }

    public QuotePricingWorkspace approve(String quoteId, QuoteApprovalCommand command, long operatorId) {
        long id = accessibleId(quoteId);
        QuotePricingState state = requireState(id, false);
        requireDraftVersion(state, command == null ? -1 : command.rowVersion());
        Prepared prepared = prepare(state, storedCommand(state));
        validateApprovalCommand(command, prepared.inputHash());
        JsonNode current = state.approval(); Instant now = time.now();
        if (current == null || !"requested".equals(current.path("status").asText())
                || !prepared.inputHash().equals(current.path("input_hash").asText())
                || !after(current.path("expires_at").asText(null), now)) {
            throw new ServiceException("例外申请不存在、已过期或报价内容已变化", 409);
        }
        if (Long.toString(operatorId).equals(current.path("requested_by").asText())) {
            throw new ServiceException("例外申请人不能自行批准", 403);
        }
        ObjectNode approval = objectMapper.createObjectNode();
        current.properties().forEach(entry -> approval.set(entry.getKey(), entry.getValue().deepCopy()));
        approval.put("status", "approved");
        approval.put("approved_by", Long.toString(operatorId)); approval.put("approved_at", now.toString());
        approval.put("approval_reason", command.reason().trim());
        if (!repository.updateApproval(id, approval, prepared.inputHash(), state.rowVersion(), operatorId, now)) {
            throw conflict();
        }
        return get(quoteId);
    }

    public QuotePricingWorkspace confirm(String quoteId, QuoteConfirmCommand command, long operatorId) {
        long id = accessibleId(quoteId);
        if (command == null || command.inputHash() == null) throw new ServiceException("确认参数不能为空");
        return transaction.execute(status -> catalogWriteLock.executeLocked(5, () -> {
            QuotePricingState state = requireState(id, true);
            if ("confirmed".equals(state.status()) && command.inputHash().equals(state.contentHash())) {
                return workspace(state, null);
            }
            requireDraftVersion(state, command.rowVersion());
            Prepared prepared = prepare(state, storedCommand(state));
            if (!command.inputHash().equals(prepared.inputHash())) {
                throw new ServiceException("报价内容或当前商品数据已变化，请重新核价", 409);
            }
            if (!prepared.issues().isEmpty()) {
                throw new ServiceException("报价仍有阻断项：" + prepared.issues().get(0).message(), 409);
            }
            if (prepared.calculation().approvalRequired() && !approvalValid(state.approval(), prepared.inputHash(), time.now())) {
                throw new ServiceException("当前报价需要有效负责人例外", 409);
            }
            Instant now = time.now();
            QuotePricingWrite write = write(state, storedCommand(state), prepared, state.approval(), operatorId, now);
            if (!repository.confirm(write, operatorId, now.plus(state.validDays(), ChronoUnit.DAYS))) throw conflict();
            return workspace(requireState(id, false), null);
        }));
    }

    private Prepared prepare(QuotePricingState state, QuotePricingCommand command) {
        Map<Long, QuotePricingCommand.Line> requestedLines = new HashMap<>();
        if (command.lines() != null) for (QuotePricingCommand.Line line : command.lines()) {
            long id = FashionId.parse(line.detailId()).value();
            if (requestedLines.put(id, line) != null) throw new ServiceException("报价明细不能重复");
        }
        Set<Long> selectedIds = new HashSet<>();
        if (command.selectedComboIds() != null) command.selectedComboIds().forEach(id -> {
            if (!selectedIds.add(FashionId.parse(id).value())) throw new ServiceException("报价组合不能重复选择");
        });
        List<Combo> combos = new ArrayList<>();
        Set<Long> knownCombos = new HashSet<>(); Set<Long> knownLines = new HashSet<>();
        for (QuotePricingState.Combo combo : state.combos()) {
            knownCombos.add(combo.id()); boolean selected = selectedIds.contains(combo.id());
            List<Line> lines = new ArrayList<>();
            for (QuotePricingState.Line fact : combo.lines()) {
                knownLines.add(fact.id()); QuotePricingCommand.Line requested = requestedLines.get(fact.id());
                if (selected && requested == null) throw new ServiceException("已选组合必须提交全部尺码行");
                int qty = requested == null ? fact.qty() : requested.qty();
                BigDecimal price = requested == null
                        ? (fact.quotePrice() == null ? fact.currentPrice() : fact.quotePrice()) : requested.quotePrice();
                if (price == null) throw new ServiceException("SKU " + fact.skuCode() + " 缺少当前价格");
                lines.add(new Line(fact.id(), fact.slotCode(), fact.productId(), fact.skuCode(), qty, price,
                        fact.currentPrice(), fact.currentCurrency(), fact.currentTaxMode(), fact.currentPriceAsOf(),
                        fact.currentPriceBatchId(), fact.currentStockQty(), fact.currentStockAsOf(),
                        fact.currentStockBatchId(), "active".equals(fact.currentStatus())));
            }
            combos.add(new Combo(combo.id(), combo.categoryCount(), combo.setQty(), selected, List.copyOf(lines)));
        }
        if (!knownCombos.containsAll(selectedIds)) throw new ServiceException("报价包含未知组合");
        if (!knownLines.containsAll(requestedLines.keySet())) throw new ServiceException("报价包含未知明细");
        Request request = new Request(command.mode(), command.taxMode(), command.taxRate(), command.feeTaxable(),
                command.discountType(), command.discountRate(), command.fixedDiscount(), command.freight(), combos);
        Calculation calculation = calculator.calculate(request, time.now(), freshnessHours());
        List<Issue> issues = new ArrayList<>(calculation.issues());
        state.combos().stream().filter(combo -> selectedIds.contains(combo.id()))
                .filter(combo -> combo.setQty() != state.requestedQty())
                .forEach(combo -> issues.add(new Issue("QUOTE_QTY_MISMATCH", "组合 " + combo.comboNo()
                        + " 的采购套数 " + combo.setQty() + " 与方案套数 " + state.requestedQty()
                        + " 不一致，请重新生成搭配和尺码分配")));
        state.combos().stream().filter(combo -> selectedIds.contains(combo.id()))
                .filter(combo -> combo.selectedImageId() != null && !combo.selectedImageValid())
                .forEach(combo -> issues.add(new Issue("SELECTED_IMAGE_INVALID",
                        "组合 " + combo.comboNo() + " 的采用图已失效或不再满足人工复核要求")));
        state.combos().stream().filter(combo -> selectedIds.contains(combo.id())).forEach(combo -> combo.lines().forEach(line -> {
            if (line.currentImageKey() == null || line.currentImageHash() == null) {
                issues.add(new Issue("IMAGE_UNAVAILABLE", "SKU " + line.skuCode() + " 缺少当前可用原图"));
            } else if (!line.currentImageAllowed()) {
                issues.add(new Issue("IMAGE_PERMISSION_DENIED", "SKU " + line.skuCode() + " 的当前原图未获方案使用许可"));
            } else if (line.frozenImageVersion() != null && (line.frozenImageVersion() != line.currentVisualVersion()
                    || !line.currentImageKey().equals(line.frozenImageKey())
                    || !line.currentImageHash().equals(line.frozenImageHash()))) {
                issues.add(new Issue("IMAGE_CHANGED", "SKU " + line.skuCode() + " 的商品原图已变化"));
            }
        }));
        return new Prepared(request, calculation, List.copyOf(issues), hash(state, request, command));
    }

    private QuotePricingWrite write(QuotePricingState state, QuotePricingCommand command, Prepared prepared,
            JsonNode approval, long operatorId, Instant now) {
        Map<Long, ComboResult> comboResults = new LinkedHashMap<>();
        prepared.calculation().combos().forEach(result -> comboResults.put(result.id(), result));
        Map<Long, Line> lines = new HashMap<>();
        prepared.request().combos().forEach(combo -> combo.lines().forEach(line -> lines.put(line.id(), line)));
        List<QuotePricingWrite.Combo> combos = state.combos().stream().map(combo -> {
            Combo requestCombo = prepared.request().combos().stream().filter(value -> value.id() == combo.id())
                    .findFirst().orElseThrow();
            ComboResult result = comboResults.get(combo.id());
            boolean allocation = requestCombo.selected() && prepared.issues().stream()
                    .noneMatch(issue -> issue.code().equals("ALLOCATION_MISMATCH")
                            || issue.code().equals("EMPTY_ALLOCATION") || issue.code().equals("MISSING_SLOT"));
            List<QuotePricingWrite.Line> writes = combo.lines().stream().map(fact -> {
                Line line = lines.get(fact.id());
                return new QuotePricingWrite.Line(fact.id(), line.qty(), fact.currentPrice(), line.unitPrice(),
                        line.unitPrice().multiply(BigDecimal.valueOf(line.qty())).setScale(2, RoundingMode.HALF_UP),
                        fact.currentPriceBatchId(), fact.currentStockQty(), fact.currentStockAsOf(),
                        fact.currentStockBatchId(), fact.currentImageKey(), fact.currentImageHash(),
                        fact.currentVisualVersion());
            }).toList();
            return new QuotePricingWrite.Combo(combo.id(), requestCombo.selected(), allocation,
                    result == null ? null : result.subtotal(), result == null ? null : result.discount(),
                    result == null ? null : result.freight(), result == null ? null : result.tax(),
                    result == null ? null : result.total(), writes);
        }).toList();
        return new QuotePricingWrite(state.quoteId(), state.rowVersion(), command.mode(), command.taxMode(),
                command.taxRate(), command.feeTaxable(), command.discountType(), command.discountRate(),
                command.fixedDiscount(), command.freight(), command.validDays(), trim(command.publicNote()),
                prepared.calculation().subtotal(), prepared.calculation().discount(), prepared.calculation().tax(),
                prepared.calculation().total(), prepared.inputHash(), approval, combos, operatorId, now);
    }

    private QuotePricingWorkspace workspace(QuotePricingState state, QuotePricingCommand override) {
        if ("confirmed".equals(state.status())) return confirmedWorkspace(state);
        QuotePricingCommand command = override == null ? storedCommand(state) : override;
        Prepared prepared = prepare(state, command); Instant now = time.now();
        List<QuotePricingWorkspace.Combo> combos = state.combos().stream().map(combo -> new QuotePricingWorkspace.Combo(
                Long.toString(combo.id()), combo.comboNo(), combo.name(), combo.categoryCount(), combo.setQty(), combo.selected(),
                combo.allocationConfirmed(), combo.selectedImageId() == null ? null : Long.toString(combo.selectedImageId()),
                combo.selectedImageNo(), combo.selectedImageValid(), money(combo.subtotal()), money(combo.discountAmount()), money(combo.freight()),
                money(combo.taxAmount()), money(combo.totalAmount()), combo.rowVersion(),
                combo.lines().stream().map(line -> new QuotePricingWorkspace.Line(
                        Long.toString(line.id()), line.slotCode(), Long.toString(line.productId()), line.skuCode(),
                        line.productName(), line.categoryCode(), line.colorName(), line.sizeCode(), line.unit(), line.qty(),
                        money(line.currentPrice()), money(line.quotePrice() == null ? line.currentPrice() : line.quotePrice()),
                        money(line.amount()),
                        line.currentStockQty(), line.currentStockAsOf(), id(line.currentPriceBatchId()),
                        id(line.currentStockBatchId()), line.frozenSourcePrice() != null && line.currentPrice() != null
                                && line.frozenSourcePrice().compareTo(line.currentPrice()) != 0,
                        line.frozenStockBatchId() != null && (!line.frozenStockBatchId().equals(line.currentStockBatchId())
                                || !java.util.Objects.equals(line.frozenStockQty(), line.currentStockQty())
                                || !java.util.Objects.equals(line.frozenStockAsOf(), line.currentStockAsOf())),
                        imageChanged(line),
                        line.rowVersion())).toList())).toList();
        return new QuotePricingWorkspace(Long.toString(state.quoteId()), state.quoteNo(), state.versionNo(),
                state.customerName(), state.requestedQty(), state.status(), state.rowVersion(), command.mode(), state.warehouseCode(),
                state.currency(), command.taxMode(), decimal(command.taxRate()), command.feeTaxable(), command.discountType(),
                decimal(command.discountRate()), money(command.fixedDiscount()), money(command.freight()), command.validDays(), state.validUntil(),
                command.publicNote(), prepared.inputHash(), prepared.calculation().approvalRequired(),
                approvalValid(state.approval(), prepared.inputHash(), now), state.approval(),
                money(prepared.calculation().subtotal()), money(prepared.calculation().discount()),
                money(prepared.calculation().tax()), money(prepared.calculation().total()),
                prepared.calculation().maximumAvailableSets(),
                prepared.calculation().combos().stream().map(this::calculated).toList(), prepared.issues(), combos);
    }

    private QuotePricingWorkspace confirmedWorkspace(QuotePricingState state) {
        List<CalculatedCombo> calculated = "alternatives".equals(state.mode()) ? state.combos().stream()
                .filter(QuotePricingState.Combo::selected)
                .map(combo -> new CalculatedCombo(Long.toString(combo.id()), money(combo.subtotal()),
                        money(combo.discountAmount()), money(combo.freight()), money(combo.taxAmount()),
                        money(combo.totalAmount()), combo.setQty() <= 0 || combo.totalAmount() == null ? null
                                : money(combo.totalAmount().divide(BigDecimal.valueOf(combo.setQty()), 2, RoundingMode.HALF_UP)),
                        frozenMaximum(combo.lines(), combo.setQty())))
                .toList() : List.of();
        List<QuotePricingWorkspace.Combo> combos = state.combos().stream().map(combo -> new QuotePricingWorkspace.Combo(
                Long.toString(combo.id()), combo.comboNo(), combo.name(), combo.categoryCount(), combo.setQty(), combo.selected(),
                combo.allocationConfirmed(), id(combo.selectedImageId()), combo.selectedImageNo(),
                combo.selectedImageValid(), money(combo.subtotal()),
                money(combo.discountAmount()), money(combo.freight()), money(combo.taxAmount()), money(combo.totalAmount()), combo.rowVersion(),
                combo.lines().stream().map(line -> new QuotePricingWorkspace.Line(Long.toString(line.id()),
                        line.slotCode(), Long.toString(line.productId()), line.skuCode(), line.productName(),
                        line.categoryCode(), line.colorName(), line.sizeCode(), line.unit(), line.qty(),
                        money(line.frozenSourcePrice()), money(line.quotePrice()), money(line.amount()), line.frozenStockQty(),
                        line.frozenStockAsOf(), id(line.frozenPriceBatchId()), id(line.frozenStockBatchId()),
                        line.frozenSourcePrice() != null && line.currentPrice() != null
                                && line.frozenSourcePrice().compareTo(line.currentPrice()) != 0,
                        !java.util.Objects.equals(line.frozenStockBatchId(), line.currentStockBatchId())
                                || !java.util.Objects.equals(line.frozenStockQty(), line.currentStockQty()),
                        imageChanged(line),
                        line.rowVersion())).toList())).toList();
        boolean approved = historicApprovalValid(state.approval(), state.contentHash());
        return new QuotePricingWorkspace(Long.toString(state.quoteId()), state.quoteNo(), state.versionNo(),
                state.customerName(), state.requestedQty(), state.status(), state.rowVersion(), state.mode(), state.warehouseCode(),
                state.currency(), state.taxMode(), decimal(state.taxRate()), state.feeTaxable(), state.discountType(),
                decimal(state.discountRate()), money(state.fixedDiscount()), money(state.freight()), state.validDays(), state.validUntil(),
                state.publicNote(), state.contentHash(), state.approval() != null, approved, state.approval(),
                money(state.subtotal()), money(state.discountAmount()), money(state.taxAmount()), money(state.totalAmount()),
                "combined".equals(state.mode()) ? frozenMaximum(state.combos().stream()
                        .filter(QuotePricingState.Combo::selected).flatMap(combo -> combo.lines().stream()).toList(),
                        state.combos().stream().filter(QuotePricingState.Combo::selected)
                                .mapToInt(QuotePricingState.Combo::setQty).sum()) : null,
                calculated,
                List.of(), combos);
    }

    private QuotePricingCommand storedCommand(QuotePricingState state) {
        List<String> selected = state.combos().stream().filter(QuotePricingState.Combo::selected)
                .map(combo -> Long.toString(combo.id())).toList();
        List<QuotePricingCommand.Line> lines = state.combos().stream().flatMap(combo -> combo.lines().stream())
                .map(line -> new QuotePricingCommand.Line(Long.toString(line.id()), line.qty(),
                        line.quotePrice() == null ? line.currentPrice() : line.quotePrice())).toList();
        return new QuotePricingCommand(state.mode(), state.taxMode(), state.taxRate(), state.feeTaxable(),
                state.discountType(), state.discountRate(), state.fixedDiscount(), state.freight(), state.validDays(),
                state.publicNote(), selected, lines, state.rowVersion());
    }

    private String hash(QuotePricingState state, Request request, QuotePricingCommand command) {
        ObjectNode root = objectMapper.createObjectNode(); root.put("schema_version", "1.0");
        root.put("quote_id", Long.toString(state.quoteId())); root.put("quote_version", state.versionNo());
        root.put("customer_id", Long.toString(state.customerId())); root.put("requested_qty", state.requestedQty());
        root.put("mode", request.mode());
        root.put("warehouse", state.warehouseCode()); root.put("tax_mode", request.taxMode());
        if (request.taxRate() == null) root.putNull("tax_rate"); else root.put("tax_rate", request.taxRate().toPlainString());
        root.put("fee_taxable", request.feeTaxable()); root.put("discount_type", request.discountType());
        root.put("discount_rate", request.discountRate().toPlainString());
        root.put("fixed_discount", request.fixedDiscount().toPlainString()); root.put("freight", request.freight().toPlainString());
        root.put("valid_days", command.validDays());
        if (command.publicNote() == null) root.putNull("public_note"); else root.put("public_note", command.publicNote().trim());
        ArrayNode combos = root.putArray("combos");
        request.combos().stream().filter(Combo::selected).forEach(combo -> {
            ObjectNode c = combos.addObject(); c.put("id", Long.toString(combo.id())); c.put("set_qty", combo.setQty());
            QuotePricingState.Combo comboFact = state.combos().stream().filter(value -> value.id() == combo.id())
                    .findFirst().orElseThrow();
            c.put("combo_visual_hash", comboFact.visualHash());
            if (comboFact.selectedImageId() == null) c.putNull("selected_image_id");
            else c.put("selected_image_id", Long.toString(comboFact.selectedImageId()));
            if (comboFact.selectedImageNo() == null) c.putNull("selected_image_no");
            else c.put("selected_image_no", comboFact.selectedImageNo());
            if (comboFact.selectedImageHash() == null) c.putNull("selected_image_hash");
            else c.put("selected_image_hash", comboFact.selectedImageHash());
            c.put("selected_image_valid", comboFact.selectedImageValid());
            ArrayNode lines = c.putArray("lines"); combo.lines().forEach(line -> {
                ObjectNode l = lines.addObject(); l.put("id", Long.toString(line.id()));
                l.put("product_id", Long.toString(line.productId())); l.put("qty", line.qty());
                l.put("unit_price", line.unitPrice().toPlainString()); l.put("price_batch", id(line.priceBatchId()));
                l.put("stock_batch", id(line.stockBatchId()));
                QuotePricingState.Line fact = state.combos().stream().flatMap(value -> value.lines().stream())
                        .filter(value -> value.id() == line.id()).findFirst().orElseThrow();
                l.put("product_version", fact.currentProductRowVersion());
                l.put("stock_version", fact.currentStockRowVersion()); l.put("visual_version", fact.currentVisualVersion());
                l.put("image_hash", fact.currentImageHash()); l.put("image_allowed", fact.currentImageAllowed());
            });
        });
        try { return FashionHashing.sha256(objectMapper.writeValueAsBytes(root)); }
        catch (Exception exception) { throw new ServiceException("报价内容无法编码"); }
    }

    private void validateTerms(QuotePricingCommand command) {
        if (command == null || command.validDays() < 1 || command.validDays() > 30) {
            throw new ServiceException("报价有效期必须为 1～30 天");
        }
        if (command.publicNote() != null && command.publicNote().length() > 2000) {
            throw new ServiceException("公开备注不能超过 2000 字符");
        }
    }
    private static void validateApprovalCommand(QuoteApprovalCommand command, String expectedHash) {
        if (command == null || command.inputHash() == null || !command.inputHash().equals(expectedHash)
                || command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 500) {
            throw new ServiceException("例外请求参数或报价输入摘要无效", 409);
        }
    }
    private static boolean approvalValid(JsonNode approval, String hash, Instant now) {
        return approval != null && "approved".equals(approval.path("status").asText())
                && hash.equals(approval.path("input_hash").asText())
                && after(approval.path("expires_at").asText(null), now)
                && !approval.path("approved_by").asText().equals(approval.path("requested_by").asText());
    }
    private static boolean historicApprovalValid(JsonNode approval, String hash) {
        return approval != null && "approved".equals(approval.path("status").asText())
                && hash != null && hash.equals(approval.path("input_hash").asText())
                && !approval.path("approved_by").asText().equals(approval.path("requested_by").asText());
    }
    private static boolean after(String value, Instant now) {
        try { return value != null && Instant.parse(value).isAfter(now); }
        catch (Exception exception) { return false; }
    }
    private int freshnessHours() {
        String value = config.selectConfigByKey("fashion.stock.freshnessHours");
        try { int parsed = Integer.parseInt(value == null ? "24" : value); if (parsed < 1 || parsed > 168) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException exception) { throw new ServiceException("库存新鲜度配置无效"); }
    }
    private QuotePricingState requireState(long id, boolean forUpdate) {
        return repository.find(id, forUpdate).orElseThrow(() -> new ServiceException("报价方案不存在"));
    }
    private long accessibleId(String quoteId) {
        long id = FashionId.parse(quoteId).value(); quotes.requireAccessible(id); return id;
    }
    private static void requireDraftVersion(QuotePricingState state, long rowVersion) {
        if (!"draft".equals(state.status())) throw new ServiceException("只有草稿报价可以修改或确认", 409);
        if (state.rowVersion() != rowVersion) throw conflict();
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private CalculatedCombo calculated(ComboResult value) {
        return new CalculatedCombo(Long.toString(value.id()), money(value.subtotal()), money(value.discount()),
                money(value.freight()), money(value.tax()), money(value.total()), money(value.averagePerSet()),
                value.maximumAvailableSets());
    }
    private static String money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String decimal(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static boolean imageChanged(QuotePricingState.Line line) {
        return line.frozenImageVersion() != null && (line.frozenImageVersion() != line.currentVisualVersion()
                || !java.util.Objects.equals(line.frozenImageKey(), line.currentImageKey())
                || !java.util.Objects.equals(line.frozenImageHash(), line.currentImageHash()));
    }
    private static Integer frozenMaximum(List<QuotePricingState.Line> lines, int nominalSets) {
        if (lines.isEmpty() || nominalSets <= 0 || lines.stream().anyMatch(line -> line.frozenStockQty() == null)) {
            return null;
        }
        Map<Long, Integer> required = new LinkedHashMap<>();
        Map<Long, Integer> stock = new LinkedHashMap<>();
        lines.forEach(line -> {
            required.merge(line.productId(), line.qty(), Integer::sum);
            stock.putIfAbsent(line.productId(), line.frozenStockQty());
        });
        long maximum = Integer.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : required.entrySet()) {
            maximum = Math.min(maximum, (long) stock.get(entry.getKey()) * nominalSets / entry.getValue());
        }
        return (int) Math.max(0, maximum);
    }
    private static String id(Long value) { return value == null ? null : Long.toString(value); }
    private static ServiceException conflict() { return new ServiceException("报价已被其他操作修改，请刷新", 409); }
    private record Prepared(Request request, Calculation calculation, List<Issue> issues, String inputHash) {}
}
