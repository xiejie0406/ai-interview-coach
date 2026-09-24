package com.ruoyi.fashion.application.material;

import java.nio.charset.StandardCharsets;
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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.importing.ImportDetailView;
import com.ruoyi.fashion.application.importing.ImportRowError;
import com.ruoyi.fashion.application.importing.port.FashionImportRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.product.FashionProductService;
import com.ruoyi.fashion.application.product.port.FashionProductRepository;
import com.ruoyi.fashion.application.security.FashionDictionaryGuard;
import com.ruoyi.fashion.domain.importing.FashionImportBatch;
import com.ruoyi.fashion.domain.importing.FashionImportDetail;
import com.ruoyi.fashion.domain.product.FashionProduct;
import com.ruoyi.fashion.domain.product.FashionProductImage;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.persistence.FashionCatalogWriteLock;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import com.ruoyi.fashion.infrastructure.storage.FashionUploadPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FashionProductMaterialService {
    private final FashionImportRepository imports;
    private final FashionProductRepository products;
    private final FashionDictionaryGuard dictionaryGuard;
    private final FashionObjectStoragePort storage;
    private final FashionIdGenerator idGenerator;
    private final FashionTimeSource timeSource;
    private final FashionCatalogWriteLock catalogWriteLock;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public FashionProductMaterialService(
            FashionImportRepository imports,
            FashionProductRepository products,
            FashionDictionaryGuard dictionaryGuard,
            FashionObjectStoragePort storage,
            FashionIdGenerator idGenerator,
            FashionTimeSource timeSource,
            FashionCatalogWriteLock catalogWriteLock,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction,
            ObjectMapper objectMapper) {
        this.imports = imports;
        this.products = products;
        this.dictionaryGuard = dictionaryGuard;
        this.storage = storage;
        this.idGenerator = idGenerator;
        this.timeSource = timeSource;
        this.catalogWriteLock = catalogWriteLock;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
    }

    public MaterialImportView preview(
            String sourceCode,
            List<MaterialFile> uploads,
            List<ImageMapping> mappings,
            Instant asOf,
            long operatorId) {
        dictionaryGuard.requireActiveValue("fashion_product_source", sourceCode);
        Instant now = timeSource.now();
        if (asOf == null || asOf.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new ServiceException("图片取得时间不能为空或晚于服务器时间 5 分钟以上");
        }
        List<MaterialFile> files = expand(uploads);
        if (files.size() > FashionUploadPolicy.MAX_ARCHIVE_ENTRIES) {
            throw new ServiceException("一次最多上传 " + FashionUploadPolicy.MAX_ARCHIVE_ENTRIES + " 张图片");
        }
        List<ImageMapping> mappingList = mappings == null ? List.of() : List.copyOf(mappings);
        Map<String, ImageMapping> mappingByName = mappings(mappingList);
        List<Map<String, String>> manifest = files.stream()
                .map(file -> Map.of("filename", file.filename(), "sha256", FashionHashing.sha256(file.content())))
                .sorted(Comparator.comparing(item -> item.get("filename"))).toList();
        String bundleHash = stableHash(Map.of("files", manifest, "mappings", mappingList, "sourceCode", sourceCode,
                "asOf", asOf.toString()));
        java.util.Optional<FashionImportBatch> existing = imports.findBatchByRequestKey(bundleHash);
        if (existing.isPresent()) {
            return view(existing.get());
        }
        byte[] manifestBytes = jsonBytes(manifest);
        String manifestKey = "imports/image/" + bundleHash + "/manifest.json";
        storage.putIfAbsent(manifestKey, manifestBytes, "application/json");

        long batchId = idGenerator.nextId();
        List<FashionImportDetail> details = new ArrayList<>();
        int errorCount = 0;
        Set<Long> targeted = new HashSet<>();
        Map<Long, Integer> mainSelections = new HashMap<>();
        for (int index = 0; index < files.size(); index++) {
            MaterialFile file = files.get(index);
            ImageMapping mapping = mappingByName.get(file.filename());
            List<ImportRowError> errors = new ArrayList<>();
            FashionUploadPolicy.ImageInspection inspection = null;
            try {
                inspection = FashionUploadPolicy.validateImage(file.filename(), file.content());
            } catch (ServiceException exception) {
                errors.add(error("file", "invalid_image", file.filename(), exception.getMessage()));
            }
            if (mapping == null) {
                errors.add(error("mapping", "missing_mapping", file.filename(), "图片没有明确映射，保持待匹配"));
            } else {
                validateMapping(mapping, errors);
            }
            List<FashionProduct> targets = mapping == null ? List.of() : resolveTargets(sourceCode, mapping, errors);
            if (mapping != null && mapping.main()) {
                for (FashionProduct target : targets) {
                    if (mainSelections.merge(target.id(), 1, Integer::sum) > 1) {
                        errors.add(error("main", "multiple_main_images", file.filename(),
                                "同一商品在一个批次中只能选择一张主图"));
                    }
                }
            }
            targets.forEach(product -> targeted.add(product.id()));
            String sha256 = FashionHashing.sha256(file.content());
            String objectKey = null;
            if (inspection != null) {
                objectKey = "materials/" + sha256 + "/original." + inspection.extension();
                storage.putIfAbsent(objectKey, file.content(), inspection.contentType());
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("filename", file.filename());
            normalized.put("sha256", sha256);
            normalized.put("objectKey", objectKey);
            normalized.put("width", inspection == null ? null : inspection.width());
            normalized.put("height", inspection == null ? null : inspection.height());
            normalized.put("mapping", mapping);
            normalized.put("targetProductIds", targets.stream().map(p -> Long.toString(p.id())).toList());
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("table", "fq_product");
            before.put("targets", targets.stream().map(FashionProductMaterialService::imageSnapshot).toList());
            if (!errors.isEmpty()) {
                errorCount++;
            }
            Instant created = now.plusNanos(index);
            details.add(new FashionImportDetail(
                    idGenerator.nextId(), batchId, index + 1, index + 1,
                    targets.size() == 1 ? targets.get(0).id() : null,
                    sourceCode + ":image:" + file.filename(), Map.of("filename", file.filename()), normalized,
                    before, null, errors.isEmpty() ? "valid" : "invalid", List.copyOf(errors),
                    targets.isEmpty() ? null : "changed", operatorId, created, operatorId, created, 1L, "input"));
        }
        Map<Long, FashionProduct> targetProducts = new LinkedHashMap<>();
        targeted.forEach(id -> products.findById(id).ifPresent(product -> targetProducts.put(id, product)));
        String baseHash = productVersionsHash(targetProducts.values());
        FashionImportBatch batch = new FashionImportBatch(
                batchId, "IMAGE-" + batchId, "fashion-image-mapping", "1.0", "image", "import",
                sourceCode, uploads.size() == 1 ? uploads.get(0).filename() : files.size() + "-images",
                manifestKey, bundleHash, Map.of("format", "image-mapping-v1"),
                Map.of("sourceCode", sourceCode, "targetProductIds", targeted.stream().map(String::valueOf).sorted().toList()),
                stableHash(targeted.stream().map(String::valueOf).sorted().toList()), baseHash, asOf, bundleHash,
                0, files.size(), errorCount, errorCount == 0 ? "validated" : "invalid", now, null,
                errorCount == 0 ? null : "存在 " + errorCount + " 个待修正图片项",
                operatorId, now, operatorId, now, 1L, null, null, null);
        transaction.executeWithoutResult(status -> {
            imports.insertBatch(batch);
            imports.insertDetails(details);
        });
        return MaterialImportView.from(batch, details.stream().map(ImportDetailView::from).toList());
    }

    public MaterialImportView get(String batchId) {
        FashionImportBatch batch = requireImageBatch(FashionId.parse(batchId).value());
        return view(batch);
    }

    public MaterialImportView confirm(String batchId, long rowVersion, long operatorId) {
        long id = FashionId.parse(batchId).value();
        FashionImportBatch before = requireImageBatch(id);
        if ("success".equals(before.status())) {
            return view(before);
        }
        if (!"validated".equals(before.status()) || before.errorCount() != 0) {
            throw new ServiceException("只有全部明确映射并人工确认颜色的图片批次可以生效");
        }
        Boolean applied = transaction.execute(status -> catalogWriteLock.executeLocked(10, () -> {
            FashionImportBatch batch = requireImageBatch(id);
            List<FashionImportDetail> details = imports.findDetails(id);
            Set<Long> ids = targetIds(details);
            Map<Long, FashionProduct> current = new LinkedHashMap<>();
            ids.forEach(productId -> current.put(productId, products.findById(productId)
                    .orElseThrow(() -> new ServiceException("图片目标商品不存在"))));
            if (!Objects.equals(batch.baseDataHash(), productVersionsHash(current.values()))) {
                imports.markBatchConflict(id, operatorId, timeSource.now(), "目标商品或图片版本已变化，请重新预览");
                return false;
            }
            Instant now = timeSource.now();
            if (!imports.markPublishing(id, rowVersion, operatorId, now)) {
                throw new ServiceException("图片批次已被其他操作修改，请刷新后重试");
            }
            Map<Long, List<FashionImportDetail>> byProduct = new LinkedHashMap<>();
            for (FashionImportDetail detail : details) {
                for (Long productId : detailTargetIds(detail)) {
                    byProduct.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(detail);
                }
            }
            for (Map.Entry<Long, List<FashionImportDetail>> entry : byProduct.entrySet()) {
                FashionProduct product = current.get(entry.getKey());
                List<FashionProductImage> images = new ArrayList<>(product.images());
                String mainImage = product.mainImageKey();
                boolean changed = false;
                for (FashionImportDetail detail : entry.getValue()) {
                    ImageMapping mapping = objectMapper.convertValue(detail.normalizedData().get("mapping"), ImageMapping.class);
                    String sha = text(detail.normalizedData(), "sha256");
                    String objectKey = text(detail.normalizedData(), "objectKey");
                    boolean duplicate = images.stream().anyMatch(image -> image.sha256().equals(sha));
                    if (!duplicate) {
                        if (images.size() >= 5) {
                            throw new ServiceException("商品 " + product.skuCode() + " 当前图片已达到 5 张上限");
                        }
                        images.add(new FashionProductImage(
                                Long.toString(idGenerator.nextId()), objectKey, sha, mapping.usage(), mapping.sourceType(),
                                mapping.jdId(), mapping.sourceUrl(), mapping.capturedAt(), true, mapping.allowAi(),
                                mapping.allowProposal(), mapping.allowEcommerce(), "active", operatorId, now,
                                number(detail.normalizedData(), "width"), number(detail.normalizedData(), "height"),
                                text(detail.normalizedData(), "filename")));
                        changed = true;
                    }
                    if (mapping.main() && !Objects.equals(mainImage, objectKey)) {
                        mainImage = objectKey;
                        changed = true;
                    }
                }
                if (changed && !products.updateImages(
                        product.id(), images, mainImage, product.rowVersion(), operatorId)) {
                    throw new ServiceException("图片生效发生商品并发冲突");
                }
            }
            for (FashionImportDetail detail : details) {
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("table", "fq_product");
                after.put("targets", detailTargetIds(detail).stream()
                        .map(productId -> products.findById(productId).map(FashionProductMaterialService::imageSnapshot).orElseThrow())
                        .toList());
                imports.markDetailApplied(detail.id(), detail.productId(), after, operatorId, now);
            }
            imports.markBatchSuccess(id, operatorId, now);
            return true;
        }));
        if (!Boolean.TRUE.equals(applied)) {
            throw new ServiceException("目标商品或图片版本已变化，请重新预览");
        }
        return view(requireImageBatch(id));
    }

    private List<MaterialFile> expand(List<MaterialFile> uploads) {
        if (uploads == null || uploads.isEmpty() || uploads.size() > 200) {
            throw new ServiceException("一次必须上传 1～200 个文件");
        }
        List<MaterialFile> result = new ArrayList<>();
        for (MaterialFile file : uploads) {
            String name = FashionUploadPolicy.safeFilename(file.filename());
            if ("zip".equals(FashionUploadPolicy.extension(name))) {
                FashionUploadPolicy.extractRawEntries(file.content())
                        .forEach(entry -> result.add(new MaterialFile(entry.filename(), entry.content())));
            } else {
                result.add(file);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, ImageMapping> mappings(List<ImageMapping> mappings) {
        if (mappings == null) {
            return Map.of();
        }
        Map<String, ImageMapping> result = new LinkedHashMap<>();
        for (ImageMapping mapping : mappings) {
            String name = FashionUploadPolicy.safeFilename(mapping.filename());
            if (result.putIfAbsent(name, mapping) != null) {
                throw new ServiceException("图片映射文件名重复：" + name);
            }
        }
        return result;
    }

    private void validateMapping(ImageMapping mapping, List<ImportRowError> errors) {
        if (!mapping.colorConfirmed()) {
            errors.add(error("colorConfirmed", "manual_confirmation_required", "false", "同款颜色必须人工确认"));
        }
        if (!Set.of("main", "detail").contains(mapping.usage())) {
            errors.add(error("usage", "invalid_usage", mapping.usage(), "图片用途必须为 main 或 detail"));
        }
        if (!Set.of("jd_plugin", "manual").contains(mapping.sourceType())) {
            errors.add(error("sourceType", "invalid_source_type", mapping.sourceType(), "来源必须为 jd_plugin 或 manual"));
        }
        if (mapping.sourceUrl() != null && !mapping.sourceUrl().startsWith("https://")) {
            errors.add(error("sourceUrl", "invalid_https_url", mapping.sourceUrl(), "图片来源链接必须是 HTTPS"));
        }
        if (mapping.sourceUrl() != null && mapping.sourceUrl().length() > 2048) {
            errors.add(error("sourceUrl", "too_long", mapping.sourceUrl(), "图片来源链接不能超过 2048 字符"));
        }
        if (mapping.jdId() != null && mapping.jdId().length() > 64) {
            errors.add(error("jdId", "too_long", mapping.jdId(), "京东商品 ID 不能超过 64 字符"));
        }
        if (mapping.capturedAt() == null) {
            errors.add(error("capturedAt", "required", null, "图片取得时间不能为空"));
        } else if (mapping.capturedAt().isAfter(timeSource.now().plus(5, ChronoUnit.MINUTES))) {
            errors.add(error("capturedAt", "future_time", mapping.capturedAt().toString(),
                    "图片取得时间不能晚于服务器时间 5 分钟以上"));
        }
        boolean sku = mapping.skuCode() != null && !mapping.skuCode().isBlank();
        boolean styleColor = mapping.styleCode() != null && !mapping.styleCode().isBlank()
                && mapping.colorCode() != null && !mapping.colorCode().isBlank();
        if (sku == styleColor) {
            errors.add(error("mapping", "ambiguous_target", null, "必须且只能按 SKU 或款号＋颜色映射"));
        }
    }

    private List<FashionProduct> resolveTargets(
            String sourceCode, ImageMapping mapping, List<ImportRowError> errors) {
        if (mapping.skuCode() != null && !mapping.skuCode().isBlank()) {
            List<FashionProduct> found = new ArrayList<>(products.findByBusinessKeys(
                    Set.of(FashionProductService.businessKey(sourceCode, mapping.skuCode()))).values());
            if (found.isEmpty()) {
                errors.add(error("skuCode", "unknown_product", mapping.skuCode(), "未找到来源内 SKU"));
            }
            return found;
        }
        if (mapping.styleCode() != null && mapping.colorCode() != null) {
            List<FashionProduct> found = products.findByStyleColor(sourceCode, mapping.styleCode(), mapping.colorCode());
            if (found.isEmpty()) {
                errors.add(error("styleCode", "unknown_style_color", mapping.styleCode(), "未找到款号＋颜色"));
            }
            return found;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> targetIds(List<FashionImportDetail> details) {
        Set<Long> result = new HashSet<>();
        details.forEach(detail -> result.addAll(detailTargetIds(detail)));
        return result;
    }

    private static List<Long> detailTargetIds(FashionImportDetail detail) {
        Object raw = detail.normalizedData().get("targetProductIds");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(value -> Long.parseLong(value.toString())).toList();
    }

    private String productVersionsHash(java.util.Collection<FashionProduct> values) {
        return stableHash(values.stream().sorted(Comparator.comparingLong(FashionProduct::id))
                .map(product -> Map.of(
                        "id", Long.toString(product.id()), "rowVersion", product.rowVersion(),
                        "visualVersion", product.visualVersion())).toList());
    }

    private static Map<String, Object> imageSnapshot(FashionProduct product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", Long.toString(product.id()));
        result.put("rowVersion", product.rowVersion());
        result.put("visualVersion", product.visualVersion());
        result.put("mainImageKey", product.mainImageKey());
        result.put("images", product.images());
        return result;
    }

    private MaterialImportView view(FashionImportBatch batch) {
        return MaterialImportView.from(batch,
                imports.findDetails(batch.id()).stream().map(ImportDetailView::from).toList());
    }

    private FashionImportBatch requireImageBatch(long id) {
        FashionImportBatch batch = imports.findBatchById(id)
                .orElseThrow(() -> new ServiceException("图片导入批次不存在"));
        if (!"image".equals(batch.importType())) {
            throw new ServiceException("批次不是图片导入");
        }
        return batch;
    }

    private static ImportRowError error(String field, String code, String value, String message) {
        String summary = value == null ? "[empty]" : (value.length() > 32 ? value.substring(0, 32) + "…" : value);
        return new ImportRowError(field, code, summary, message);
    }

    private byte[] jsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException exception) {
            throw new ServiceException("无法生成图片清单");
        }
    }

    private String stableHash(Object value) {
        try {
            return FashionHashing.sha256(objectMapper.writer()
                    .with(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(value));
        } catch (JacksonException exception) {
            throw new ServiceException("无法计算图片批次摘要");
        }
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
}
