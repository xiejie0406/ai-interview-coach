package com.ruoyi.fashion.application.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.agent.run.FashionRuntimeException;
import com.ruoyi.fashion.application.image.port.FashionImageProviderPort;
import com.ruoyi.fashion.application.image.port.FashionImageProviderPort.ProviderResult;
import com.ruoyi.fashion.application.image.port.FashionQuoteImageRepository;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.application.selection.SelectionComboView;
import com.ruoyi.fashion.application.selection.port.FashionSelectionRepository;
import com.ruoyi.fashion.configuration.FashionImageProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import com.ruoyi.fashion.infrastructure.storage.FashionUploadPolicy;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 图片任务、复核、采用与费用状态的唯一业务入口。 */
@Service
public class FashionQuoteImageService {
    private static final Set<String> IMAGE_TYPES = Set.of("model", "styling", "ecommerce", "composition");
    private static final Set<String> CREATE_MODES = Set.of("sample", "provider", "composition");
    private static final Set<String> PARAMETER_FIELDS = Set.of(
            "aspectRatio", "modelPresentation", "scene", "pose", "promptVersion", "background", "shadow");
    private static final Set<String> REVIEW_CHECKS = Set.of(
            "slot_count", "style", "color", "logo", "completeness", "pose", "quality");
    private static final Set<String> FAILURE_REASONS = Set.of(
            "missing_item", "wrong_style", "wrong_color", "logo_error", "abnormal_pose", "low_quality", "other");

    private final FashionQuoteImageRepository repository;
    private final FashionSelectionRepository selections;
    private final FashionQuoteDraftService quotes;
    private final FashionObjectStoragePort storage;
    private final FashionImageProviderPort provider;
    private final FashionImageProperties properties;
    private final ISysConfigService config;
    private final FashionIdGenerator ids;
    private final FashionTimeSource time;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final FashionTelemetry telemetry;

    public FashionQuoteImageService(
            FashionQuoteImageRepository repository,
            FashionSelectionRepository selections,
            FashionQuoteDraftService quotes,
            FashionObjectStoragePort storage,
            FashionImageProviderPort provider,
            FashionImageProperties properties,
            ISysConfigService config,
            FashionIdGenerator ids,
            FashionTimeSource time,
            ObjectMapper objectMapper,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction,
            FashionTelemetry telemetry) {
        this.repository = repository;
        this.selections = selections;
        this.quotes = quotes;
        this.storage = storage;
        this.provider = provider;
        this.properties = properties;
        this.config = config;
        this.ids = ids;
        this.time = time;
        this.objectMapper = objectMapper;
        this.transaction = transaction;
        this.telemetry = telemetry;
    }

    public QuoteImageWorkspace workspace(String quoteId) {
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(quoteId).value());
        Instant month = monthStart(time.now());
        return new QuoteImageWorkspace(Long.toString(quote.id()), quote.rowVersion(), quote.status(),
                providerEnabled(), providerEnabled() ? null : "provider_disabled", monthlyBudget(),
                repository.settledCostSince(month), selections.findByQuoteId(quote.id()),
                repository.findByQuoteId(quote.id()).stream().map(this::viewWithCurrentState).toList());
    }

    public QuoteImageView create(String quoteId, QuoteImageCreateCommand command, long operatorId) {
        if (command == null || !CREATE_MODES.contains(command.sourceMode())) {
            throw new ServiceException("图片任务来源必须是 sample、composition 或 provider");
        }
        Prepared prepared = prepare(quoteId, command);
        JsonNode parameters = sanitizedParameters(command.parameters());
        Optional<QuoteImageTask> existing = repository.findByRequestKey(command.requestKey());
        if (existing.isPresent()) {
            QuoteImageTask value = existing.get();
            if (!matchesIntent(value, prepared, command, parameters)) {
                throw new ServiceException("requestKey 已用于不同图片任务", 409);
            }
            return viewWithCurrentState(value);
        }
        Instant now = time.now();
        long id = ids.nextId();
        transaction.executeWithoutResult(status -> {
            repository.lockMonthlyBudget();
            Optional<QuoteImageTask> duplicate = repository.findByRequestKey(command.requestKey());
            if (duplicate.isPresent()) return;
            Cost cost = cost(command.sourceMode(), command.requestedCount());
            QuoteImageTask task = new QuoteImageTask(id, prepared.quote().id(), prepared.comboId(), null, null,
                    command.imageType(), command.sourceMode(), prepared.inputHash(), prepared.inputs(),
                    parameters, command.requestedCount(), objectMapper.createArrayNode(), null, null,
                    command.requestKey(), "queued", false, 0, now, null, cost.estimated(), null,
                    cost.currency(), cost.billingStatus(), cost.events(), null, null, now, false, null, 1L);
            repository.insert(task, operatorId, now);
        });
        QuoteImageTask saved = repository.findByRequestKey(command.requestKey()).orElseThrow();
        if (!matchesIntent(saved, prepared, command, parameters)) {
            throw new ServiceException("requestKey 已用于不同图片任务", 409);
        }
        telemetry.recordImageTaskEvent(command.sourceMode(), "created", 1);
        return viewWithCurrentState(saved);
    }

    public QuoteImageView upload(String quoteId, String comboId, String imageType, String requestKey,
            long quoteRowVersion, long comboRowVersion, String comboVisualHash,
            String filename, byte[] content, long operatorId) {
        QuoteImageCreateCommand command = new QuoteImageCreateCommand(comboId, imageType, "upload", 1,
                objectMapper.createObjectNode(), requestKey, quoteRowVersion, comboRowVersion, comboVisualHash);
        Prepared prepared = prepareUpload(quoteId, command);
        FashionUploadPolicy.ImageInspection inspection = FashionUploadPolicy.validateImage(filename, content);
        String sha = FashionHashing.sha256(content);
        Optional<QuoteImageTask> existing = repository.findByRequestKey(requestKey);
        if (existing.isPresent()) {
            QuoteImageTask value = existing.get();
            JsonNode prior = value.results().isArray() && !value.results().isEmpty() ? value.results().get(0) : null;
            if (!matchesIntent(value, prepared, command, objectMapper.createObjectNode())
                    || prior == null || !sha.equals(prior.path("sha256").asText())) {
                throw new ServiceException("requestKey 已用于不同上传图片", 409);
            }
            return viewWithCurrentState(value);
        }
        long id = ids.nextId();
        String key = "quote-images/" + id + "/upload/" + sha + "." + inspection.extension();
        StoredFashionObject stored = storage.putIfAbsent(key, content, inspection.contentType());
        ArrayNode results = objectMapper.createArrayNode();
        ObjectNode result = results.addObject();
        result.put("no", 1); result.put("status", "success"); result.put("object_key", stored.objectKey());
        result.put("sha256", stored.sha256()); result.put("width", inspection.width());
        result.put("height", inspection.height()); result.putNull("error");
        result.put("allow_proposal", true); result.put("allow_ecommerce", "ecommerce".equals(imageType));
        result.putArray("reviews");
        Instant now = time.now();
        QuoteImageTask task = new QuoteImageTask(id, prepared.quote().id(), prepared.comboId(), null, null,
                imageType, "upload", prepared.inputHash(), prepared.inputs(), objectMapper.createObjectNode(), 1,
                results, null, null, requestKey, "success", false, 0, null, null, null, null, null,
                "not_applicable", objectMapper.createArrayNode(), null, now, now, false, null, 1L);
        transaction.executeWithoutResult(status -> {
            if (repository.findByRequestKey(requestKey).isEmpty()) repository.insert(task, operatorId, now);
        });
        QuoteImageTask saved = repository.findByRequestKey(requestKey).orElseThrow();
        JsonNode savedResult = saved.results().isArray() && !saved.results().isEmpty() ? saved.results().get(0) : null;
        if (!matchesIntent(saved, prepared, command, objectMapper.createObjectNode())
                || savedResult == null || !sha.equals(savedResult.path("sha256").asText())) {
            throw new ServiceException("requestKey 已用于不同上传图片", 409);
        }
        telemetry.recordImageTaskEvent("upload", "created", 1);
        return viewWithCurrentState(saved);
    }

    public QuoteImageView cancel(String quoteId, String imageId, long rowVersion, long operatorId) {
        QuoteImageTask task = requireAccessible(quoteId, imageId);
        if (!repository.requestCancel(task.id(), rowVersion, operatorId, time.now())) {
            throw new ServiceException("图片任务状态已变化，无法取消", 409);
        }
        telemetry.recordImageTaskEvent(task.sourceMode(), "cancel-requested", 1);
        return viewWithCurrentState(repository.findById(task.id()).orElseThrow());
    }

    public QuoteImageView review(String quoteId, String imageId, QuoteImageReviewCommand command, long operatorId) {
        QuoteImageTask task = requireAccessible(quoteId, imageId);
        if (command != null && task.rowVersion() != command.rowVersion()) {
            throw new ServiceException("图片复核版本已变化，请刷新", 409);
        }
        if (command == null || command.resultNo() < 1 || command.resultNo() > 4
                || !("pass".equals(command.decision()) || "reject".equals(command.decision()))) {
            throw new ServiceException("图片复核参数无效");
        }
        boolean stale = stale(task);
        if (stale && !task.stale()) repository.markStale(task.id(), task.rowVersion(), operatorId, time.now());
        task = repository.findById(task.id()).orElseThrow();
        if (!Set.of("success", "partial").contains(task.status())) throw new ServiceException("当前任务没有可复核结果");
        ObjectNode result = result(task.results(), command.resultNo());
        if (!"success".equals(result.path("status").asText())) throw new ServiceException("失败结果不能标记通过");
        List<String> checks = command.checklist() == null ? List.of() : command.checklist().stream().distinct().toList();
        if ("pass".equals(command.decision()) && !checks.containsAll(REVIEW_CHECKS)) {
            throw new ServiceException("通过复核必须逐项确认数量、款式、颜色、标识、完整性、姿态和质量");
        }
        if ("reject".equals(command.decision())
                && (command.reason() == null || !FAILURE_REASONS.contains(command.reason()))) {
            throw new ServiceException("拒绝图片必须选择规范失败原因");
        }
        ArrayNode reviews = result.withArray("reviews");
        if (reviews.size() >= 20) throw new ServiceException("本图片复核历史已达上限，请创建关联重试任务");
        ObjectNode review = reviews.addObject();
        review.put("decision", command.decision()); review.put("reviewer", Long.toString(operatorId));
        review.put("time", time.now().toString());
        if (command.reason() == null) review.putNull("reason"); else review.put("reason", command.reason());
        review.set("checklist", objectMapper.valueToTree(checks)); review.put("input_hash", task.inputHash());
        if (!repository.updateReview(task.id(), task.results(), task.rowVersion(), operatorId, time.now())) {
            throw new ServiceException("图片复核已被其他操作更新", 409);
        }
        telemetry.recordImageTaskEvent(task.sourceMode(), "review-" + command.decision(), 1);
        return viewWithCurrentState(repository.findById(task.id()).orElseThrow());
    }

    public QuoteImageView adopt(String quoteId, String imageId, int resultNo, long rowVersion, long operatorId) {
        QuoteImageTask task = requireAccessible(quoteId, imageId);
        FashionQuote quote = quotes.requireAccessible(task.quoteId());
        if (!"draft".equals(quote.status())) throw new ServiceException("已确认或关闭方案不能替换历史采用图片");
        if (stale(task)) {
            repository.markStale(task.id(), task.rowVersion(), operatorId, time.now());
            throw new ServiceException("组合或商品原图已变化，旧图片只能留档，不能采用", 409);
        }
        ObjectNode result = result(task.results(), resultNo);
        if (!"success".equals(result.path("status").asText()) || !latestPassed(result, task.inputHash())) {
            throw new ServiceException("图片必须完成人工逐项复核后才能采用");
        }
        SelectionComboView combo = selections.findCombo(task.quoteId(), task.comboId())
                .orElseThrow(() -> new ServiceException("组合不存在"));
        if (!combo.selected()) throw new ServiceException("旧组合图片不能成为当前采用图");
        if (!repository.adopt(task.comboId(), task.id(), resultNo, combo.visualHash(), rowVersion, operatorId, time.now())) {
            throw new ServiceException("组合或图片版本已变化，请刷新", 409);
        }
        telemetry.recordImageTaskEvent(task.sourceMode(), "adopted", 1);
        return viewWithCurrentState(repository.findById(task.id()).orElseThrow());
    }

    public byte[] resultContent(String quoteId, String imageId, int resultNo) {
        QuoteImageTask task = requireAccessible(quoteId, imageId);
        ObjectNode result = result(task.results(), resultNo);
        String key = result.path("object_key").asText(null);
        if (key == null || !"success".equals(result.path("status").asText())) throw new ServiceException("图片结果不可读");
        return storage.read(key);
    }

    public byte[] originalContent(String quoteId, String comboId, String slotCode) {
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(quoteId).value());
        long parsedCombo = FashionId.parse(comboId).value();
        selections.findCombo(quote.id(), parsedCombo).orElseThrow(() -> new ServiceException("组合不存在"));
        for (JsonNode slot : repository.currentInputs(parsedCombo)) {
            if (slotCode.equals(slot.path("slot_code").asText())) {
                String key = slot.path("image_key").asText(null);
                if (key == null) break;
                return storage.read(key);
            }
        }
        throw new ServiceException("组合原图不存在");
    }

    /** 领取在事务内完成，Provider/图像处理在事务外执行，结果按领取后的 rowVersion 防栅栏提交。 */
    public boolean processNext(String workerId) {
        Instant now = time.now();
        QuoteImageTask task = transaction.execute(status -> repository.claimNext(workerId, now,
                now.plus(properties.getLeaseDuration())).orElse(null));
        if (task == null) return false;
        processClaimed(task);
        return true;
    }

    private void processClaimed(QuoteImageTask task) {
        Instant now = time.now();
        if ("cancel_requested".equals(task.status())) {
            if (task.providerTaskId() != null) provider.cancel(task.providerTaskId());
            String billing = "not_applicable".equals(task.billingStatus()) ? "not_applicable" : "unknown";
            complete(task, "cancelled", task.results(), task.providerCode(), task.providerTaskId(), task.retryCount(),
                    null, task.actualCost(), billing, task.billingEvents(), null, now);
            return;
        }
        try {
            if ("sample".equals(task.sourceMode())) {
                ArrayNode results = renderSamples(task);
                complete(task, "success", results, null, null, task.retryCount(), null, null,
                        "not_applicable", task.billingEvents(), null, now);
            } else if ("composition".equals(task.sourceMode())) {
                ArrayNode results = renderComposition(task);
                complete(task, "success", results, null, null, task.retryCount(), null, null,
                        "not_applicable", task.billingEvents(), null, now);
            } else {
                processProvider(task, now);
            }
        } catch (FashionRuntimeException exception) {
            handleRuntimeFailure(task, exception, now);
        } catch (Exception exception) {
            complete(task, "failed", task.results(), task.providerCode(), task.providerTaskId(), task.retryCount(),
                    null, task.actualCost(), task.billingStatus(), task.billingEvents(), "图片处理失败", now);
        }
    }

    private void processProvider(QuoteImageTask task, Instant now) {
        if (!providerEnabled()) throw new FashionRuntimeException("PROVIDER_DISABLED", "图片 Provider 未启用", false);
        ProviderResult result = task.providerTaskId() != null
                ? provider.query(task.providerTaskId())
                : "queued".equals(task.status())
                        ? provider.submit(task.requestKey(), task.imageType(), task.inputs(), task.parameters(),
                                task.requestedCount())
                        : provider.query(task.requestKey());
        String status = normalizeProviderStatus(result.status());
        String billing = switch (status) {
            case "success", "partial", "failed" -> result.actualCost() == null ? "unknown" : "settled";
            case "unknown", "cancelled" -> "unknown";
            default -> "reserved";
        };
        ArrayNode events = objectMapper.createArrayNode();
        task.billingEvents().forEach(event -> events.add(event.deepCopy()));
        if (result.actualCost() != null && events.size() < 20) {
            ObjectNode event = events.addObject(); event.put("event_key", "settle:" + result.providerTaskId());
            event.put("amount", result.actualCost()); event.put("currency", result.currency());
            event.put("basis", "provider_response"); event.put("time", now.toString());
        }
        boolean finished = Set.of("success", "partial", "failed", "cancelled").contains(status);
        complete(task, status, result.results() == null ? task.results() : result.results(), result.providerCode(),
                result.providerTaskId(), task.retryCount(), finished ? null : now.plusSeconds(2),
                result.actualCost(), billing, events, result.errorMessage(), finished ? now : null);
    }

    private void handleRuntimeFailure(QuoteImageTask task, FashionRuntimeException exception, Instant now) {
        if ("UPSTREAM_TIMEOUT".equals(exception.code()) || "UNKNOWN_ACCEPTANCE".equals(exception.code())) {
            complete(task, "unknown", task.results(), task.providerCode(), task.providerTaskId(), task.retryCount(),
                    now.plusSeconds(3), task.actualCost(), "unknown", task.billingEvents(),
                    "提交结果未知，将先按原请求键回查", null);
            return;
        }
        if (exception.retryable() && task.retryCount() < 2) {
            int retry = task.retryCount() + 1;
            complete(task, "queued", task.results(), task.providerCode(), task.providerTaskId(), retry,
                    now.plusSeconds(1L << retry), task.actualCost(), task.billingStatus(), task.billingEvents(),
                    "网络错误，等待有限重试", null);
            return;
        }
        complete(task, "failed", task.results(), task.providerCode(), task.providerTaskId(), task.retryCount(),
                null, task.actualCost(), task.billingStatus(), task.billingEvents(), exception.getMessage(), now);
    }

    private void complete(QuoteImageTask task, String status, JsonNode results, String providerCode,
            String providerTaskId, int retryCount, Instant nextRetryAt, BigDecimal actualCost,
            String billingStatus, JsonNode billingEvents, String error, Instant finishedAt) {
        Instant now = time.now();
        transaction.executeWithoutResult(tx -> {
            if (!repository.updateExecution(task.id(), task.rowVersion(), status, results, providerCode,
                    providerTaskId, retryCount, nextRetryAt, actualCost, billingStatus, billingEvents,
                    safeError(error), finishedAt, now)) {
                throw new ServiceException("图片任务租约已失效，晚到结果未被采用", 409);
            }
        });
        if (finishedAt == null) {
            telemetry.recordImageTaskEvent(task.sourceMode(), status, 1);
        } else {
            Duration duration = Duration.between(task.createTime(), now);
            if (duration.isNegative()) duration = Duration.ZERO;
            telemetry.recordImageTaskCompletion(task.sourceMode(), status, duration,
                    actualCost == null ? 0d : actualCost.doubleValue());
        }
    }

    private Prepared prepare(String quoteId, QuoteImageCreateCommand command) {
        validateCommand(command);
        if ("provider".equals(command.sourceMode()) && !providerEnabled()) {
            throw new ServiceException("真实图片 Provider 未启用");
        }
        return prepareCommon(quoteId, command, "provider".equals(command.sourceMode()));
    }

    private Prepared prepareUpload(String quoteId, QuoteImageCreateCommand command) {
        validateCommand(command);
        return prepareCommon(quoteId, command, false);
    }

    private Prepared prepareCommon(String quoteId, QuoteImageCreateCommand command, boolean requiresAiPermission) {
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(quoteId).value());
        if (!"draft".equals(quote.status()) || quote.rowVersion() != command.quoteRowVersion()) {
            throw new ServiceException("只有当前草稿方案可以创建图片任务", 409);
        }
        long comboId = FashionId.parse(command.comboId()).value();
        SelectionComboView combo = selections.findCombo(quote.id(), comboId)
                .orElseThrow(() -> new ServiceException("图片任务组合不存在"));
        if (!combo.selected() || combo.rowVersion() != command.comboRowVersion()
                || !combo.visualHash().equals(command.comboVisualHash())) {
            throw new ServiceException("组合已变化，请刷新后重试", 409);
        }
        JsonNode slots = repository.currentInputs(comboId);
        if (!slots.isArray() || slots.isEmpty() || slots.size() > 4) throw new ServiceException("组合必须包含 1～4 个图片槽位");
        slots.forEach(slot -> {
            if (slot.path("image_key").isNull() || slot.path("image_hash").isNull()) {
                throw new ServiceException("组合商品缺少可用原图");
            }
            JsonNode permission = slot.path("permission_snapshot");
            if (!permission.path("allow_proposal").asBoolean(false)) throw new ServiceException("商品原图未获方案使用许可");
            if (requiresAiPermission && !permission.path("allow_ai").asBoolean(false)) {
                throw new ServiceException("商品原图未获 AI 使用许可");
            }
            if ("ecommerce".equals(command.imageType()) && !permission.path("allow_ecommerce").asBoolean(false)) {
                throw new ServiceException("商品原图未获电商图片许可");
            }
        });
        ObjectNode inputs = objectMapper.createObjectNode();
        inputs.put("schema_version", "1.0"); inputs.put("combo_id", Long.toString(comboId));
        inputs.put("combo_visual_hash", combo.visualHash()); inputs.set("slots", slots);
        return new Prepared(quote, comboId, inputs, hash(inputs));
    }

    private void validateCommand(QuoteImageCreateCommand command) {
        if (!IMAGE_TYPES.contains(command.imageType())) throw new ServiceException("图片类型无效");
        int max = maxResults();
        if (command.requestedCount() < 1 || command.requestedCount() > max) {
            throw new ServiceException("候选图片数量必须在 1～" + max + " 之间");
        }
        if (command.requestKey() == null || !command.requestKey().matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
            throw new ServiceException("requestKey 必须是 8～128 位稳定请求标识");
        }
    }

    private JsonNode sanitizedParameters(JsonNode parameters) {
        ObjectNode result = objectMapper.createObjectNode();
        if (parameters == null || parameters.isNull()) return result;
        if (!parameters.isObject()) throw new ServiceException("图片参数必须是对象");
        parameters.propertyNames().forEach(name -> {
            if (!PARAMETER_FIELDS.contains(name)) throw new ServiceException("图片参数包含未允许字段：" + name);
            JsonNode value = parameters.get(name);
            if (!value.isTextual() && !value.isBoolean()) throw new ServiceException("图片参数值类型无效：" + name);
            if (value.isTextual() && value.asText().length() > 200) throw new ServiceException("图片参数过长：" + name);
            result.set(name, value.deepCopy());
        });
        if (jsonBytes(result).length > 4096) throw new ServiceException("图片参数超过 4 KiB 上限");
        return result;
    }

    private Cost cost(String sourceMode, int count) {
        if (!"provider".equals(sourceMode)) return new Cost(null, null, "not_applicable", objectMapper.createArrayNode());
        BigDecimal estimated = properties.getEstimatedCostPerResult().multiply(BigDecimal.valueOf(count));
        BigDecimal budget = monthlyBudget();
        BigDecimal committed = repository.committedCostSince(monthStart(time.now()));
        if (budget.signum() <= 0 || committed.add(estimated).compareTo(budget) > 0) {
            throw new ServiceException("AI 月度预算不足，未提交 Provider");
        }
        ArrayNode events = objectMapper.createArrayNode();
        ObjectNode event = events.addObject(); event.put("event_key", "reserve"); event.put("amount", estimated);
        event.put("currency", "CNY"); event.put("basis", "configured_estimate"); event.put("time", time.now().toString());
        return new Cost(estimated, "CNY", "reserved", events);
    }

    private ArrayNode renderSamples(QuoteImageTask task) throws Exception {
        ArrayNode results = objectMapper.createArrayNode();
        for (int no = 1; no <= task.requestedCount(); no++) {
            BufferedImage image = canvas(768, 1024, new Color(238 - no * 8, 243, 248));
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(31, 41, 55)); graphics.setFont(new Font("SansSerif", Font.BOLD, 42));
            graphics.drawString("FASHION SAMPLE", 170, 450);
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 25));
            graphics.drawString("体验样例 · 非实时 AI 生成", 190, 510);
            graphics.drawString("候选 " + no + " / " + task.requestedCount(), 280, 560); graphics.dispose();
            addStoredResult(results, task, no, image, "sample");
        }
        return results;
    }

    private ArrayNode renderComposition(QuoteImageTask task) throws Exception {
        List<JsonNode> slots = new java.util.ArrayList<>(); task.inputs().path("slots").forEach(slots::add);
        BufferedImage output = canvas(1024, 1024, Color.WHITE);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int columns = slots.size() == 1 ? 1 : 2; int rows = (slots.size() + columns - 1) / columns;
        int cellW = 1024 / columns; int cellH = 1024 / rows;
        for (int index = 0; index < slots.size(); index++) {
            JsonNode slot = slots.get(index); BufferedImage source = ImageIO.read(new ByteArrayInputStream(
                    storage.read(slot.path("image_key").asText())));
            if (source == null) throw new ServiceException("原图格式暂不支持拼版");
            int x = (index % columns) * cellW; int y = (index / columns) * cellH;
            double scale = Math.min((cellW - 24d) / source.getWidth(), (cellH - 54d) / source.getHeight());
            int w = (int) (source.getWidth() * scale); int h = (int) (source.getHeight() * scale);
            graphics.drawImage(source, x + (cellW - w) / 2, y + 14, w, h, null);
            graphics.setColor(new Color(31, 41, 55)); graphics.setFont(new Font("SansSerif", Font.BOLD, 18));
            graphics.drawString(slot.path("slot_code").asText(), x + 12, y + cellH - 14);
        }
        graphics.dispose(); ArrayNode results = objectMapper.createArrayNode();
        addStoredResult(results, task, 1, output, "composition"); return results;
    }

    private void addStoredResult(ArrayNode results, QuoteImageTask task, int no, BufferedImage image, String label)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        byte[] bytes = output.toByteArray(); String sha = FashionHashing.sha256(bytes);
        String key = "quote-images/" + task.id() + "/" + label + "/" + no + "-" + sha + ".png";
        StoredFashionObject stored = storage.putIfAbsent(key, bytes, "image/png");
        ObjectNode result = results.addObject(); result.put("no", no); result.put("status", "success");
        result.put("object_key", stored.objectKey()); result.put("sha256", stored.sha256());
        result.put("width", image.getWidth()); result.put("height", image.getHeight()); result.putNull("error");
        result.put("allow_proposal", true); result.put("allow_ecommerce", "ecommerce".equals(task.imageType()));
        result.putArray("reviews");
    }

    private static BufferedImage canvas(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0, 0, width, height);
        graphics.dispose(); return image;
    }

    private QuoteImageTask requireAccessible(String quoteId, String imageId) {
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(quoteId).value());
        QuoteImageTask task = repository.findById(FashionId.parse(imageId).value())
                .orElseThrow(() -> new ServiceException("图片任务不存在"));
        if (task.quoteId() != quote.id()) throw new ServiceException("图片任务不属于当前方案");
        return task;
    }

    private QuoteImageView viewWithCurrentState(QuoteImageTask task) {
        boolean stale = task.stale() || stale(task);
        return new QuoteImageView(Long.toString(task.id()), Long.toString(task.quoteId()), Long.toString(task.comboId()),
                task.imageType(), task.sourceMode(), sourceLabel(task.sourceMode()), task.inputHash(), task.inputs(),
                task.parameters(), task.requestedCount(), task.results(), task.providerCode(), task.providerTaskId(),
                task.status(), displayStatus(task, stale), stale, task.retryCount(), task.estimatedCost(),
                task.actualCost(), task.costCurrency(), task.billingStatus(), task.errorMessage(), task.finishedAt(),
                task.createTime(), task.rowVersion());
    }

    private boolean stale(QuoteImageTask task) {
        JsonNode currentSlots = repository.currentInputs(task.comboId());
        ObjectNode current = objectMapper.createObjectNode(); current.put("schema_version", "1.0");
        current.put("combo_id", Long.toString(task.comboId()));
        SelectionComboView combo = selections.findCombo(task.quoteId(), task.comboId()).orElse(null);
        if (combo == null || !combo.selected()) return true;
        current.put("combo_visual_hash", combo.visualHash()); current.set("slots", currentSlots);
        return !task.inputHash().equals(hash(current));
    }

    private static String displayStatus(QuoteImageTask task, boolean stale) {
        if (stale) return "needs_review";
        if (task.adopted()) return "adopted";
        if ("queued".equals(task.status())) return "queued";
        if (Set.of("running", "unknown", "cancel_requested").contains(task.status())) return task.status();
        if (Set.of("failed", "cancelled").contains(task.status())) return task.status();
        boolean passed = false; boolean rejected = false;
        for (JsonNode result : task.results()) {
            JsonNode reviews = result.path("reviews");
            if (reviews.isArray() && !reviews.isEmpty()) {
                String decision = reviews.get(reviews.size() - 1).path("decision").asText();
                passed |= "pass".equals(decision); rejected |= "reject".equals(decision);
            }
        }
        return passed ? "not_adopted" : rejected ? "not_adopted" : "pending_review";
    }

    private static String sourceLabel(String sourceMode) {
        return Map.of("sample", "体验样例", "upload", "外部上传", "provider", "真实 Provider",
                "composition", "商品原图拼版", "reuse", "历史复用").getOrDefault(sourceMode, sourceMode);
    }

    private static ObjectNode result(JsonNode results, int no) {
        if (results != null && results.isArray()) for (JsonNode value : results) {
            if (value.path("no").asInt() == no && value.isObject()) return (ObjectNode) value;
        }
        throw new ServiceException("图片结果不存在");
    }

    private static boolean latestPassed(ObjectNode result, String inputHash) {
        JsonNode reviews = result.path("reviews");
        if (!reviews.isArray() || reviews.isEmpty()) return false;
        JsonNode latest = reviews.get(reviews.size() - 1);
        return "pass".equals(latest.path("decision").asText()) && inputHash.equals(latest.path("input_hash").asText());
    }

    private boolean providerEnabled() { return properties.isProviderEnabled() && provider.available(); }
    private int maxResults() { return parseIntConfig("fashion.image.maxResults", 2, 1, 4); }
    private BigDecimal monthlyBudget() {
        String value = config.selectConfigByKey("fashion.ai.monthlyBudgetCny");
        try { return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value); }
        catch (NumberFormatException exception) { throw new ServiceException("AI 月度预算配置无效"); }
    }
    private int parseIntConfig(String key, int fallback, int min, int max) {
        String value = config.selectConfigByKey(key);
        try { int parsed = value == null || value.isBlank() ? fallback : Integer.parseInt(value);
            return Math.max(min, Math.min(max, parsed)); }
        catch (NumberFormatException exception) { return fallback; }
    }
    private String hash(JsonNode value) { return FashionHashing.sha256(jsonBytes(value)); }
    private byte[] jsonBytes(JsonNode value) {
        try { return objectMapper.writeValueAsBytes(value); }
        catch (Exception exception) { throw new ServiceException("图片任务数据无法编码"); }
    }
    private static String normalizeProviderStatus(String value) {
        return Set.of("queued", "running", "success", "partial", "failed", "cancelled", "unknown").contains(value)
                ? value : "unknown";
    }
    private static Instant monthStart(Instant now) {
        return now.atZone(ZoneOffset.UTC).withDayOfMonth(1).with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    private static String safeError(String value) {
        if (value == null) return null;
        String safe = value.replaceAll("(?i)(token|secret|authorization|api[-_ ]?key)\\s*[:=][^\\s,;]+", "$1=[REDACTED]");
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    private static boolean matchesIntent(QuoteImageTask task, Prepared prepared,
            QuoteImageCreateCommand command, JsonNode parameters) {
        return task.comboId() == prepared.comboId() && task.imageType().equals(command.imageType())
                && task.sourceMode().equals(command.sourceMode()) && task.inputHash().equals(prepared.inputHash())
                && task.requestedCount() == command.requestedCount() && task.parameters().equals(parameters);
    }

    private record Prepared(FashionQuote quote, long comboId, ObjectNode inputs, String inputHash) {}
    private record Cost(BigDecimal estimated, String currency, String billingStatus, ArrayNode events) {}
}
