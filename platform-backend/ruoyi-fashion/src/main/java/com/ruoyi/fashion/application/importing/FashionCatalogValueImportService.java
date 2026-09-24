package com.ruoyi.fashion.application.importing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.importing.port.FashionImportRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.product.port.FashionProductRepository;
import com.ruoyi.fashion.application.security.FashionDictionaryGuard;
import com.ruoyi.fashion.application.stock.port.FashionStockRepository;
import com.ruoyi.fashion.domain.importing.FashionImportBatch;
import com.ruoyi.fashion.domain.importing.FashionImportDetail;
import com.ruoyi.fashion.domain.product.FashionProduct;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.domain.stock.FashionStock;
import com.ruoyi.fashion.infrastructure.persistence.FashionCatalogWriteLock;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import com.ruoyi.fashion.infrastructure.storage.FashionUploadPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 价格/库存严格范围全量导入及按历史批次恢复。 */
@Service
public class FashionCatalogValueImportService {
    private static final String TEMPLATE_VERSION = FashionCatalogValueImportParser.TEMPLATE_VERSION;

    private final FashionCatalogValueImportParser parser;
    private final FashionImportRepository imports;
    private final FashionProductRepository products;
    private final FashionStockRepository stocks;
    private final FashionDictionaryGuard dictionaries;
    private final FashionObjectStoragePort storage;
    private final FashionIdGenerator ids;
    private final FashionTimeSource timeSource;
    private final FashionCatalogWriteLock catalogWriteLock;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public FashionCatalogValueImportService(
            FashionCatalogValueImportParser parser,
            FashionImportRepository imports,
            FashionProductRepository products,
            FashionStockRepository stocks,
            FashionDictionaryGuard dictionaries,
            FashionObjectStoragePort storage,
            FashionIdGenerator ids,
            FashionTimeSource timeSource,
            FashionCatalogWriteLock catalogWriteLock,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction,
            ObjectMapper objectMapper) {
        this.parser = parser;
        this.imports = imports;
        this.products = products;
        this.stocks = stocks;
        this.dictionaries = dictionaries;
        this.storage = storage;
        this.ids = ids;
        this.timeSource = timeSource;
        this.catalogWriteLock = catalogWriteLock;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
    }

    public CatalogImportView preview(
            String typeCode,
            String filename,
            byte[] content,
            String sourceCode,
            String categoryCode,
            String warehouseCode,
            Instant asOf,
            long operatorId) {
        CatalogImportType type = CatalogImportType.fromCode(typeCode);
        Scope scope = validateScope(type, sourceCode, categoryCode, warehouseCode, asOf);
        List<FashionProduct> expected = expectedProducts(scope);
        if (expected.isEmpty()) {
            throw new ServiceException("所选范围没有在售 SKU，不能创建全量批次");
        }
        String safeFilename = FashionUploadPolicy.safeFilename(filename);
        String fileHash = FashionHashing.sha256(content);
        String scopeHash = scopeHash(scope, expected);
        Map<Long, FashionStock> currentStocks = type == CatalogImportType.STOCK
                ? stocks.findByProductIds(expected.stream().map(FashionProduct::id).toList(), scope.warehouseCode())
                : Map.of();
        String baseDataHash = baseDataHash(type, expected, currentStocks);
        String requestKey = stableHash(Map.of(
                "type", type.code(), "fileHash", fileHash, "scopeHash", scopeHash,
                "baseDataHash", baseDataHash, "asOf", asOf.toString()));
        java.util.Optional<FashionImportBatch> existing = imports.findBatchByRequestKey(requestKey);
        if (existing.isPresent()) {
            return view(existing.get());
        }

        List<ParsedCatalogValueRow> rows = parser.parse(type, safeFilename, content);
        Map<String, FashionProduct> expectedByKey = expected.stream()
                .collect(Collectors.toMap(FashionProduct::businessKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, Long> frequencies = new HashMap<>();
        rows.forEach(row -> frequencies.merge(businessKey(row.values()), 1L, Long::sum));

        Instant now = timeSource.now();
        long batchId = ids.nextId();
        List<FashionImportDetail> details = new ArrayList<>();
        Set<String> presentExpectedKeys = new HashSet<>();
        int detailNo = 0;
        for (ParsedCatalogValueRow row : rows) {
            detailNo++;
            List<ImportRowError> errors = new ArrayList<>(row.errors());
            String key = businessKey(row.values());
            FashionProduct product = expectedByKey.get(key);
            validateCommonRow(row.values(), scope, asOf, frequencies.getOrDefault(key, 0L), product, errors);
            if (product != null) {
                presentExpectedKeys.add(key);
            }
            Map<String, Object> before = currentSnapshot(type, product, currentStocks);
            Map<String, Object> normalized = normalizeInput(
                    type, row.values(), product, product == null ? null : currentStocks.get(product.id()), errors);
            String changeType = errors.isEmpty() ? changeType(type, before, normalized) : null;
            Instant created = now.plusNanos(detailNo);
            details.add(new FashionImportDetail(
                    ids.nextId(), batchId, detailNo, row.sourceRowNo(), product == null ? null : product.id(), key,
                    new LinkedHashMap<>(row.values()), normalized, before, null,
                    errors.isEmpty() ? "valid" : "invalid", List.copyOf(errors), changeType,
                    operatorId, created, operatorId, created, 1L, "input"));
        }
        for (FashionProduct product : expected) {
            if (presentExpectedKeys.contains(product.businessKey())) {
                continue;
            }
            detailNo++;
            Instant created = now.plusNanos(detailNo);
            details.add(new FashionImportDetail(
                    ids.nextId(), batchId, detailNo, null, product.id(), product.businessKey(), null, null,
                    currentSnapshot(type, product, currentStocks), null, "invalid",
                    List.of(error("skuCode", "missing_scope_row", product.skuCode(), "严格全量缺少范围内 SKU")),
                    null, operatorId, created, operatorId, created, 1L, "missing"));
        }
        int errorCount = (int) details.stream().filter(detail -> !detail.errors().isEmpty()).count();
        String extension = FashionUploadPolicy.extension(safeFilename);
        String fileKey = "imports/" + type.code() + "/" + fileHash + "/source." + extension;
        storage.putIfAbsent(fileKey, content,
                "csv".equals(extension) ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        FashionImportBatch batch = new FashionImportBatch(
                batchId, type.code().toUpperCase() + "-" + batchId, type.templateCode(), TEMPLATE_VERSION,
                type.code(), "import", scope.sourceCode(), safeFilename, fileKey, fileHash, type.headers(),
                scope.toMap(), scopeHash, baseDataHash, asOf, requestKey,
                expected.size(), rows.size(), errorCount, errorCount == 0 ? "validated" : "invalid", now, null,
                errorCount == 0 ? null : "存在 " + errorCount + " 个阻断项", operatorId, now, operatorId, now,
                1L, null, scope.warehouseCode(), null);
        try {
            transaction.executeWithoutResult(status -> {
                imports.insertBatch(batch);
                imports.insertDetails(details);
            });
        } catch (DuplicateKeyException exception) {
            return imports.findBatchByRequestKey(requestKey).map(this::view)
                    .orElseThrow(() -> new ServiceException("重复请求竞争后无法读取原批次"));
        }
        return CatalogImportView.from(batch, details.stream().map(ImportDetailView::from).toList());
    }

    public CatalogImportView get(String typeCode, String batchId) {
        CatalogImportType type = CatalogImportType.fromCode(typeCode);
        return view(requireBatch(type, FashionId.parse(batchId).value()));
    }

    public CatalogImportView publish(String typeCode, String batchId, long rowVersion, long operatorId) {
        CatalogImportType type = CatalogImportType.fromCode(typeCode);
        long id = FashionId.parse(batchId).value();
        FashionImportBatch before = requireBatch(type, id);
        if ("success".equals(before.status())) {
            return view(before);
        }
        if (!"validated".equals(before.status()) || before.errorCount() != 0) {
            throw new ServiceException("只有严格全量校验通过的批次可以发布");
        }
        Boolean applied = transaction.execute(status -> catalogWriteLock.executeLocked(10, () -> {
            FashionImportBatch batch = requireBatch(type, id);
            Scope scope = Scope.from(batch);
            List<FashionProduct> currentProducts = expectedProducts(scope);
            Map<Long, FashionStock> currentStocks = type == CatalogImportType.STOCK
                    ? stocks.findByProductIds(currentProducts.stream().map(FashionProduct::id).toList(), scope.warehouseCode())
                    : Map.of();
            if (!Objects.equals(batch.scopeHash(), scopeHash(scope, currentProducts))
                    || !Objects.equals(batch.baseDataHash(), baseDataHash(type, currentProducts, currentStocks))) {
                imports.markBatchConflict(id, operatorId, timeSource.now(), "范围、当前值或 row_version 已变化，请重新预览");
                return false;
            }
            List<FashionImportDetail> details = imports.findDetails(id);
            if (details.size() != batch.expectedCount()
                    || details.stream().anyMatch(detail -> !"input".equals(detail.rowType()) || !"valid".equals(detail.status()))) {
                throw new ServiceException("批次不满足严格全量发布条件");
            }
            Instant now = timeSource.now();
            if (!imports.markPublishing(id, rowVersion, operatorId, now)) {
                throw new ServiceException("导入批次已被其他操作修改，请刷新后重试");
            }
            Map<String, FashionProduct> productsByKey = currentProducts.stream()
                    .collect(Collectors.toMap(FashionProduct::businessKey, Function.identity()));
            for (FashionImportDetail detail : details) {
                FashionProduct product = productsByKey.get(detail.businessKey());
                if (product == null) {
                    throw new ServiceException("发布范围中的商品已变化");
                }
                if (type == CatalogImportType.PRICE) {
                    applyPrice(batch, detail, product, operatorId, now);
                } else {
                    applyStock(batch, detail, product, currentStocks.get(product.id()), operatorId, now);
                }
            }
            imports.markBatchSuccess(id, operatorId, now);
            return true;
        }));
        if (!Boolean.TRUE.equals(applied)) {
            throw new ServiceException("范围、当前值或 row_version 已变化，请重新预览");
        }
        return view(requireBatch(type, id));
    }

    public CatalogImportView restore(
            String typeCode,
            String sourceBatchId,
            String requestKey,
            String operatorNote,
            long operatorId) {
        CatalogImportType type = CatalogImportType.fromCode(typeCode);
        FashionImportBatch source = requireBatch(type, FashionId.parse(sourceBatchId).value());
        if (!"success".equals(source.status())) {
            throw new ServiceException("只能从已成功发布的批次创建恢复批次");
        }
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 128) {
            throw new ServiceException("恢复请求键不能为空且不能超过 128 字符");
        }
        String stableRequestKey = stableHash(Map.of(
                "type", type.code(), "sourceBatchId", source.id(), "requestKey", requestKey.trim()));
        java.util.Optional<FashionImportBatch> existing = imports.findBatchByRequestKey(stableRequestKey);
        if (existing.isPresent()) {
            return view(existing.get());
        }
        Scope scope = Scope.from(source);
        List<FashionProduct> currentProducts = expectedProducts(scope);
        if (!Objects.equals(source.scopeHash(), scopeHash(scope, currentProducts))) {
            throw new ServiceException("原批次范围已变化，不能直接恢复，请重新选择当前范围");
        }
        Map<String, FashionProduct> productsByKey = currentProducts.stream()
                .collect(Collectors.toMap(FashionProduct::businessKey, Function.identity()));
        Map<Long, FashionStock> currentStocks = type == CatalogImportType.STOCK
                ? stocks.findByProductIds(currentProducts.stream().map(FashionProduct::id).toList(), scope.warehouseCode())
                : Map.of();
        List<FashionImportDetail> sourceDetails = imports.findDetails(source.id()).stream()
                .filter(detail -> "input".equals(detail.rowType()) && "applied".equals(detail.status())).toList();
        if (sourceDetails.size() != currentProducts.size()) {
            throw new ServiceException("原批次明细不完整，不能恢复");
        }
        Instant now = timeSource.now();
        long batchId = ids.nextId();
        List<FashionImportDetail> details = new ArrayList<>();
        int detailNo = 0;
        for (FashionImportDetail oldDetail : sourceDetails) {
            detailNo++;
            FashionProduct product = productsByKey.get(oldDetail.businessKey());
            if (product == null) {
                throw new ServiceException("原批次 SKU 已离开当前范围");
            }
            Map<String, Object> target = restoreTarget(type, oldDetail.beforeData());
            Map<String, Object> before = currentSnapshot(type, product, currentStocks);
            Instant created = now.plusNanos(detailNo);
            details.add(new FashionImportDetail(
                    ids.nextId(), batchId, detailNo, null, product.id(), product.businessKey(), null, target, before,
                    null, "valid", List.of(), changeType(type, before, target), operatorId, created, operatorId,
                    created, 1L, "input"));
        }
        FashionImportBatch batch = new FashionImportBatch(
                batchId, "RESTORE-" + type.code().toUpperCase() + "-" + batchId, type.templateCode(),
                TEMPLATE_VERSION, type.code(), "restore", source.sourceCode(), null, null, null,
                Map.of("sourceBatchId", Long.toString(source.id())), scope.toMap(), source.scopeHash(),
                baseDataHash(type, currentProducts, currentStocks), now, stableRequestKey,
                currentProducts.size(), details.size(), 0, "validated", now, null, null,
                operatorId, now, operatorId, now, 1L, source.id(), scope.warehouseCode(), trimNote(operatorNote));
        try {
            transaction.executeWithoutResult(status -> {
                imports.insertBatch(batch);
                imports.insertDetails(details);
            });
        } catch (DuplicateKeyException exception) {
            return imports.findBatchByRequestKey(stableRequestKey).map(this::view)
                    .orElseThrow(() -> new ServiceException("重复恢复请求竞争后无法读取原批次"));
        }
        return CatalogImportView.from(batch, details.stream().map(ImportDetailView::from).toList());
    }

    private Scope validateScope(
            CatalogImportType type,
            String sourceCode,
            String categoryCode,
            String warehouseCode,
            Instant asOf) {
        String source = requiredCode("sourceCode", sourceCode, 64);
        dictionaries.requireActiveValue("fashion_product_source", source);
        String category = trimToNull(categoryCode);
        if (category != null) {
            dictionaries.requireActiveValue("fashion_product_category", category);
        }
        String warehouse = trimToNull(warehouseCode);
        if (type == CatalogImportType.STOCK) {
            warehouse = requiredCode("warehouseCode", warehouse, 64);
            dictionaries.requireActiveValue("fashion_warehouse", warehouse);
        } else if (warehouse != null) {
            throw new ServiceException("价格导入不能指定仓库");
        }
        Instant now = timeSource.now();
        if (asOf == null || asOf.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new ServiceException("业务时间不能为空或晚于服务器时间 5 分钟以上");
        }
        return new Scope(source, category, warehouse);
    }

    private void validateCommonRow(
            Map<String, String> values,
            Scope scope,
            Instant batchAsOf,
            long frequency,
            FashionProduct product,
            List<ImportRowError> errors) {
        if (!Objects.equals(scope.sourceCode(), values.get("sourceCode"))) {
            errors.add(error("sourceCode", "scope_mismatch", values.get("sourceCode"), "文件来源不属于所选范围"));
        }
        if (scope.warehouseCode() != null && !Objects.equals(scope.warehouseCode(), values.get("warehouseCode"))) {
            errors.add(error("warehouseCode", "scope_mismatch", values.get("warehouseCode"), "文件仓库不属于所选范围"));
        }
        if (frequency > 1) {
            errors.add(error("skuCode", "duplicate_business_key", values.get("skuCode"), "同一范围 SKU 只能出现一次"));
        }
        if (product == null) {
            errors.add(error("skuCode", "unknown_or_out_of_scope", values.get("skuCode"), "SKU 不存在或不在当前在售范围"));
        }
        Instant rowAsOf = parseInstant(values.get("asOf"), errors);
        if (rowAsOf != null && !rowAsOf.equals(batchAsOf)) {
            errors.add(error("asOf", "mixed_business_time", values.get("asOf"), "所有行必须等于本批业务时间"));
        }
    }

    private Map<String, Object> normalizeInput(
            CatalogImportType type,
            Map<String, String> values,
            FashionProduct product,
            FashionStock currentStock,
            List<ImportRowError> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        if (type == CatalogImportType.PRICE) {
            BigDecimal price = parsePrice(values.get("salePrice"), errors);
            String currency = values.get("currency");
            String taxMode = values.get("taxMode");
            if (!"CNY".equals(currency)) {
                errors.add(error("currency", "unsupported_currency", currency, "首期价格币种只能为 CNY"));
            }
            if (!Set.of("included", "excluded").contains(taxMode)) {
                errors.add(error("taxMode", "invalid_tax_mode", taxMode, "含税口径只允许 included 或 excluded"));
            }
            Instant valueAsOf = parseInstant(values.get("asOf"), errors);
            if (product != null && product.priceAsOf() != null && valueAsOf != null
                    && valueAsOf.isBefore(product.priceAsOf())) {
                errors.add(error("asOf", "older_than_current", values.get("asOf"), "价格业务时间早于当前价格"));
            }
            result.put("salePrice", price == null ? null : price.toPlainString());
            result.put("currency", currency);
            result.put("taxMode", taxMode);
            result.put("asOf", valueAsOf == null ? null : valueAsOf.toString());
        } else {
            Integer qty = parseQuantity(values.get("availableQty"), errors);
            Instant valueAsOf = parseInstant(values.get("asOf"), errors);
            if (currentStock != null && valueAsOf != null && valueAsOf.isBefore(currentStock.asOf())) {
                errors.add(error("asOf", "older_than_current", values.get("asOf"), "库存业务时间早于当前库存"));
            }
            result.put("availableQty", qty);
            result.put("asOf", valueAsOf == null ? null : valueAsOf.toString());
        }
        return result;
    }

    private void applyPrice(
            FashionImportBatch batch,
            FashionImportDetail detail,
            FashionProduct product,
            long operatorId,
            Instant now) {
        Map<String, Object> target = detail.normalizedData();
        boolean exists = !Boolean.FALSE.equals(target.get("exists"));
        BigDecimal price = exists ? decimal(target.get("salePrice")) : null;
        String currency = exists ? text(target, "currency") : "CNY";
        String taxMode = exists ? text(target, "taxMode") : "included";
        Instant asOf = exists ? instant(target.get("asOf")) : null;
        Long sourceBatchId = exists ? batch.id() : null;
        if (!products.updatePrice(product.id(), price, currency, taxMode, asOf, sourceBatchId,
                product.rowVersion(), operatorId, now)) {
            throw new ServiceException("价格发布发生商品并发冲突");
        }
        FashionProduct saved = products.findById(product.id())
                .orElseThrow(() -> new IllegalStateException("更新后的商品不存在"));
        imports.markDetailApplied(detail.id(), product.id(), priceSnapshot(saved), operatorId, now);
    }

    private void applyStock(
            FashionImportBatch batch,
            FashionImportDetail detail,
            FashionProduct product,
            FashionStock current,
            long operatorId,
            Instant now) {
        Map<String, Object> target = detail.normalizedData();
        boolean exists = !Boolean.FALSE.equals(target.get("exists"));
        if (!exists) {
            if (current != null && !stocks.delete(current.id(), current.rowVersion())) {
                throw new ServiceException("库存恢复删除发生并发冲突");
            }
            imports.markDetailApplied(detail.id(), product.id(), stockSnapshot(product, null), operatorId, now);
            return;
        }
        int qty = integer(target.get("availableQty"));
        Instant asOf = instant(target.get("asOf"));
        String confirmationType = "restore".equals(batch.operationType()) ? "restore" : "import";
        if (current == null) {
            stocks.insert(new FashionStock(
                    ids.nextId(), product.id(), batch.warehouseCode(), qty, asOf, confirmationType,
                    null, null, batch.id(), operatorId, now, operatorId, now, 1L));
        } else if (!stocks.update(current.id(), qty, asOf, confirmationType, null, null, batch.id(),
                current.rowVersion(), operatorId, now)) {
            throw new ServiceException("库存发布发生并发冲突");
        }
        FashionStock saved = stocks.findByProductIds(List.of(product.id()), batch.warehouseCode()).get(product.id());
        imports.markDetailApplied(detail.id(), product.id(), stockSnapshot(product, saved), operatorId, now);
    }

    private List<FashionProduct> expectedProducts(Scope scope) {
        return products.findActiveForScope(scope.sourceCode(), scope.categoryCode());
    }

    private String scopeHash(Scope scope, List<FashionProduct> expected) {
        return stableHash(Map.of(
                "scope", new TreeMap<>(scope.toMap()),
                "products", expected.stream().sorted(Comparator.comparing(FashionProduct::businessKey))
                        .map(product -> Map.of("id", Long.toString(product.id()), "businessKey", product.businessKey()))
                        .toList()));
    }

    private String baseDataHash(
            CatalogImportType type,
            List<FashionProduct> expected,
            Map<Long, FashionStock> currentStocks) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (FashionProduct product : expected.stream().sorted(Comparator.comparing(FashionProduct::businessKey)).toList()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("productId", Long.toString(product.id()));
            value.put("businessKey", product.businessKey());
            value.put("productRowVersion", product.rowVersion());
            if (type == CatalogImportType.PRICE) {
                value.put("salePrice", string(product.salePrice()));
                value.put("currency", product.currency());
                value.put("taxMode", product.taxMode());
                value.put("asOf", string(product.priceAsOf()));
                value.put("batchId", string(product.lastPriceImportBatchId()));
            } else {
                FashionStock stock = currentStocks.get(product.id());
                value.put("stock", stock == null ? Map.of("exists", false) : stockFields(stock));
            }
            values.add(value);
        }
        return stableHash(values);
    }

    private Map<String, Object> currentSnapshot(
            CatalogImportType type,
            FashionProduct product,
            Map<Long, FashionStock> currentStocks) {
        if (product == null) {
            return Map.of("exists", false);
        }
        return type == CatalogImportType.PRICE
                ? priceSnapshot(product)
                : stockSnapshot(product, currentStocks.get(product.id()));
    }

    private static Map<String, Object> priceSnapshot(FashionProduct product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", "fq_product");
        result.put("exists", product.salePrice() != null);
        result.put("productId", Long.toString(product.id()));
        result.put("productRowVersion", product.rowVersion());
        result.put("salePrice", string(product.salePrice()));
        result.put("currency", product.currency());
        result.put("taxMode", product.taxMode());
        result.put("asOf", string(product.priceAsOf()));
        result.put("batchId", string(product.lastPriceImportBatchId()));
        return result;
    }

    private static Map<String, Object> stockSnapshot(FashionProduct product, FashionStock stock) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", "fq_stock");
        result.put("exists", stock != null);
        result.put("productId", Long.toString(product.id()));
        result.put("productRowVersion", product.rowVersion());
        if (stock != null) {
            result.putAll(stockFields(stock));
        }
        return result;
    }

    private static Map<String, Object> stockFields(FashionStock stock) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockId", Long.toString(stock.id()));
        result.put("warehouseCode", stock.warehouseCode());
        result.put("availableQty", stock.availableQty());
        result.put("asOf", stock.asOf().toString());
        result.put("confirmationType", stock.confirmationType());
        result.put("batchId", Long.toString(stock.lastImportBatchId()));
        result.put("stockRowVersion", stock.rowVersion());
        return result;
    }

    private static Map<String, Object> restoreTarget(CatalogImportType type, Map<String, Object> old) {
        if (old == null) {
            throw new ServiceException("原批次缺少 before_data，不能恢复");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        boolean exists = Boolean.TRUE.equals(old.get("exists"));
        result.put("exists", exists);
        if (type == CatalogImportType.PRICE) {
            result.put("salePrice", old.get("salePrice"));
            result.put("currency", old.getOrDefault("currency", "CNY"));
            result.put("taxMode", old.getOrDefault("taxMode", "included"));
            result.put("asOf", old.get("asOf"));
        } else if (exists) {
            result.put("availableQty", old.get("availableQty"));
            result.put("asOf", old.get("asOf"));
        }
        return result;
    }

    private static String changeType(CatalogImportType type, Map<String, Object> before, Map<String, Object> target) {
        if (!Boolean.TRUE.equals(before.get("exists")) && Boolean.TRUE.equals(target.get("exists"))) {
            return "added";
        }
        if (type == CatalogImportType.PRICE) {
            return Objects.equals(before.get("salePrice"), target.get("salePrice"))
                    && Objects.equals(before.get("currency"), target.get("currency"))
                    && Objects.equals(before.get("taxMode"), target.get("taxMode"))
                    && Objects.equals(before.get("asOf"), target.get("asOf")) ? "unchanged" : "changed";
        }
        return Objects.equals(before.get("availableQty"), target.get("availableQty"))
                && Objects.equals(before.get("asOf"), target.get("asOf")) ? "unchanged" : "changed";
    }

    private CatalogImportView view(FashionImportBatch batch) {
        return CatalogImportView.from(batch,
                imports.findDetails(batch.id()).stream().map(ImportDetailView::from).toList());
    }

    private FashionImportBatch requireBatch(CatalogImportType type, long id) {
        FashionImportBatch batch = imports.findBatchById(id)
                .orElseThrow(() -> new ServiceException("导入批次不存在"));
        if (!type.code().equals(batch.importType())) {
            throw new ServiceException("批次类型与请求路径不一致");
        }
        return batch;
    }

    private String stableHash(Object value) {
        try {
            return FashionHashing.sha256(objectMapper.writer()
                    .with(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(value));
        } catch (JacksonException exception) {
            throw new ServiceException("无法计算导入摘要");
        }
    }

    private static BigDecimal parsePrice(String value, List<ImportRowError> errors) {
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.signum() < 0 || result.scale() > 2 || result.precision() - result.scale() > 14) {
                throw new NumberFormatException();
            }
            return result.setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException exception) {
            errors.add(error("salePrice", "invalid_price", value, "销售单价必须为非负且最多两位小数"));
            return null;
        }
    }

    private static Integer parseQuantity(String value, List<ImportRowError> errors) {
        try {
            int result = Integer.parseInt(value);
            if (result < 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            errors.add(error("availableQty", "invalid_quantity", value, "可售数量必须为非负整数"));
            return null;
        }
    }

    private static Instant parseInstant(String value, List<ImportRowError> errors) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            errors.add(error("asOf", "invalid_business_time", value, "业务时间必须是 ISO-8601 UTC 时间"));
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static String businessKey(Map<String, String> values) {
        return Objects.toString(values.get("sourceCode"), "") + ":" + Objects.toString(values.get("skuCode"), "");
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String requiredCode(String name, String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength || !normalized.matches("[A-Za-z0-9_.-]+")) {
            throw new ServiceException(name + " 必须是 1～" + maxLength + " 位安全编码");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimNote(String value) {
        String note = trimToNull(value);
        if (note != null && note.length() > 1000) {
            throw new ServiceException("恢复说明不能超过 1000 字符");
        }
        return note;
    }

    private static ImportRowError error(String field, String code, String value, String message) {
        String summary = value == null ? "[empty]" : (value.length() > 32 ? value.substring(0, 32) + "…" : value);
        return new ImportRowError(field, code, summary, message);
    }

    private record Scope(String sourceCode, String categoryCode, String warehouseCode) {
        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourceCode", sourceCode);
            if (categoryCode != null) result.put("categoryCode", categoryCode);
            if (warehouseCode != null) result.put("warehouseCode", warehouseCode);
            result.put("mode", "strict_full");
            return Map.copyOf(result);
        }

        static Scope from(FashionImportBatch batch) {
            return new Scope(
                    Objects.toString(batch.scope().get("sourceCode"), batch.sourceCode()),
                    string(batch.scope().get("categoryCode")),
                    batch.warehouseCode() == null ? string(batch.scope().get("warehouseCode")) : batch.warehouseCode());
        }
    }
}
