package com.ruoyi.fashion.application.agent.run;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.agent.AgentVersionView;
import com.ruoyi.fashion.application.agent.port.FashionAgentRepository;
import com.ruoyi.fashion.application.agent.run.port.FashionRequirementRuntimePort;
import com.ruoyi.fashion.application.agent.run.port.FashionRunRepository;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
import com.ruoyi.fashion.application.quote.QuoteView;
import com.ruoyi.fashion.application.product.FashionProductService;
import com.ruoyi.fashion.application.product.ProductView;
import com.ruoyi.fashion.application.selection.FashionSelectionService;
import com.ruoyi.fashion.application.selection.SelectionApplyResult;
import com.ruoyi.fashion.configuration.FashionAiRuntimeProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@FashionModuleEnabled
@Service
public class FashionRequirementRunService {
    private final FashionRunRepository runs;
    private final FashionAgentRepository agents;
    private final FashionQuoteDraftService quotes;
    private final FashionProductService products;
    private final FashionSelectionService selection;
    private final FashionRequirementRuntimePort runtime;
    private final FashionAiRuntimeProperties properties;
    private final FashionIdGenerator ids;
    private final FashionTimeSource time;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final FashionTelemetry telemetry;

    public FashionRequirementRunService(
            FashionRunRepository runs,
            FashionAgentRepository agents,
            FashionQuoteDraftService quotes,
            FashionProductService products,
            FashionSelectionService selection,
            FashionRequirementRuntimePort runtime,
            FashionAiRuntimeProperties properties,
            FashionIdGenerator ids,
            FashionTimeSource time,
            ObjectMapper objectMapper,
            FashionTelemetry telemetry,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction) {
        this.runs = runs;
        this.agents = agents;
        this.quotes = quotes;
        this.products = products;
        this.selection = selection;
        this.runtime = runtime;
        this.properties = properties;
        this.ids = ids;
        this.time = time;
        this.objectMapper = objectMapper;
        this.telemetry = telemetry;
        this.transaction = transaction;
    }

    public RunCapability capability() {
        if (!runtime.available()) return new RunCapability(false, "provider_disabled");
        if (agents.findCurrentVersionByType("requirement").isEmpty()) {
            return new RunCapability(false, "requirement_agent_not_published");
        }
        return new RunCapability(true, null);
    }

    public RunCapability productCapability() {
        if (!runtime.available()) return new RunCapability(false, "provider_disabled");
        if (agents.findCurrentVersionByType("selection").isEmpty()) {
            return new RunCapability(false, "product_attribute_agent_not_published");
        }
        return new RunCapability(true, null);
    }

    public RunCapability selectionCapability() {
        if (!runtime.available()) return new RunCapability(false, "provider_disabled");
        if (agents.findCurrentVersionByType("selection").isEmpty()) {
            return new RunCapability(false, "selection_agent_not_published");
        }
        return new RunCapability(true, null);
    }

    public RunView create(RequirementRunCommand command, long operatorId) {
        if (command == null) throw new ServiceException("需求分析命令不能为空");
        requireRequestKey(command.requestKey());
        String sourceText = command.sourceText() == null ? null : command.sourceText().trim();
        if (sourceText == null || sourceText.length() < 8 || sourceText.length() > 2000) {
            throw new ServiceException("需求原文必须为 8～2000 个字符");
        }
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(command.quoteId()).value());
        if (!"draft".equals(quote.status())) {
            throw new ServiceException("只有草稿方案可发起需求分析");
        }
        String sourceHash = inputHash(quote, sourceText);
        Optional<RunView> existing = runs.findByRequestKey(command.requestKey());
        if (existing.isPresent()) {
            if (!sourceHash.equals(existing.get().contextSnapshot().path("source_hash").asText())) {
                throw new ServiceException("相同 requestKey 的输入摘要不同", 409);
            }
            return existing.get();
        }
        RunCapability capability = capability();
        if (!capability.enabled()) {
            throw new ServiceException("AI 需求分析未启用：" + capability.reason(), 503);
        }
        AgentVersionView version = agents.findCurrentVersionByType("requirement").orElseThrow();
        Instant now = time.now();
        Instant deadline = now.plusSeconds(Math.min(120, version.timeoutSeconds()));
        RunView created = transaction.execute(status -> {
            Optional<RunView> concurrent = runs.findByRequestKey(command.requestKey());
            if (concurrent.isPresent()) {
                if (!sourceHash.equals(concurrent.get().contextSnapshot().path("source_hash").asText())) {
                    throw new ServiceException("相同 requestKey 的输入摘要不同", 409);
                }
                return concurrent.get();
            }
            return runs.insertRequirementRun(ids.nextId(), ids.nextId(), ids.nextId(), quote, version,
                    sourceText, sourceHash, command.requestKey(), deadline, operatorId, now);
        });
        telemetry.recordAgentRunEvent("requirement-analysis", "queued", 1);
        return created;
    }

    public RunView get(String id) {
        RunView run = requireRun(FashionId.parse(id).value());
        if (run.quoteId() != null) quotes.requireAccessible(FashionId.parse(run.quoteId()).value());
        return run;
    }

    public RunView getByCorrelationId(String correlationId) {
        if (correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            throw new ServiceException("correlationId 格式无效");
        }
        RunView run = runs.findByRunNo(correlationId)
                .orElseThrow(() -> new ServiceException("Agent Run 不存在"));
        if (run.quoteId() != null) quotes.requireAccessible(FashionId.parse(run.quoteId()).value());
        return run;
    }

    public RunView createProductAttribute(ProductAttributeRunCommand command, long operatorId) {
        if (command == null) throw new ServiceException("商品属性建议命令不能为空");
        requireRequestKey(command.requestKey());
        ProductView product = products.get(command.productId());
        String sourceHash = productAttributeSourceHash(product);
        Optional<RunView> existing = runs.findByRequestKey(command.requestKey());
        if (existing.isPresent()) {
            if (!sourceHash.equals(existing.get().contextSnapshot().path("source_hash").asText())) {
                throw new ServiceException("相同 requestKey 的商品快照不同", 409);
            }
            return existing.get();
        }
        RunCapability capability = productCapability();
        if (!capability.enabled()) throw new ServiceException("AI 商品属性建议未启用：" + capability.reason(), 503);
        AgentVersionView version = agents.findCurrentVersionByType("selection").orElseThrow();
        Instant now = time.now();
        Instant deadline = now.plusSeconds(Math.min(120, version.timeoutSeconds()));
        RunView created = transaction.execute(status -> {
            Optional<RunView> concurrent = runs.findByRequestKey(command.requestKey());
            if (concurrent.isPresent()) return concurrent.get();
            return runs.insertProductAttributeRun(ids.nextId(), ids.nextId(), ids.nextId(), product, version,
                    sourceHash, command.requestKey(), deadline, operatorId, now);
        });
        telemetry.recordAgentRunEvent("product-attribute-suggestion", "queued", 1);
        return created;
    }

    public RunView createSelection(SelectionRunCommand command, long operatorId) {
        if (command == null) throw new ServiceException("选品搭配命令不能为空");
        requireRequestKey(command.requestKey());
        FashionQuote quote = quotes.requireAccessible(FashionId.parse(command.quoteId()).value());
        JsonNode snapshot = selection.freezeForRun(command.quoteId(), command.baseComboId(), command.comboVisualHash());
        String sourceHash = FashionHashing.sha256(write(snapshot));
        Optional<RunView> existing = runs.findByRequestKey(command.requestKey());
        if (existing.isPresent()) {
            if (!sourceHash.equals(existing.get().contextSnapshot().path("source_hash").asText())) {
                throw new ServiceException("相同 requestKey 的冻结候选不同", 409);
            }
            return existing.get();
        }
        RunCapability capability = selectionCapability();
        if (!capability.enabled()) throw new ServiceException("AI 选品搭配未启用：" + capability.reason(), 503);
        AgentVersionView version = agents.findCurrentVersionByType("selection").orElseThrow();
        Instant now = time.now();
        Instant deadline = now.plusSeconds(Math.min(120, version.timeoutSeconds()));
        RunView created = transaction.execute(status -> {
            Optional<RunView> concurrent = runs.findByRequestKey(command.requestKey());
            if (concurrent.isPresent()) return concurrent.get();
            return runs.insertSelectionRun(ids.nextId(), ids.nextId(), ids.nextId(), quote, version,
                    snapshot, sourceHash, command.requestKey(), deadline, operatorId, now);
        });
        telemetry.recordAgentRunEvent("selection-styling", "queued", 1);
        return created;
    }

    public RunView cancel(String id, long expectedRowVersion, long operatorId) {
        long runId = FashionId.parse(id).value();
        get(id);
        RunView cancelled = transaction.execute(status -> {
            if (!runs.cancel(runId, expectedRowVersion, operatorId, time.now())) {
                throw new ServiceException("任务已进入不可取消状态或版本已变更", 409);
            }
            return requireRun(runId);
        });
        telemetry.recordAgentRunEvent("run", "cancel-requested", 1);
        return cancelled;
    }

    public QuoteView applyRequirement(
            String id, String applyRequestKey, long expectedQuoteRowVersion, long operatorId) {
        requireRequestKey(applyRequestKey);
        long runId = FashionId.parse(id).value();
        RunView run = get(id);
        if (!"succeeded".equals(run.status()) || !"requirement".equals(run.outputType())
                || run.output() == null || !run.output().isObject()) {
            throw new ServiceException("只有成功的需求分析任务可采用");
        }
        String inputHash = FashionHashing.sha256((run.outputHash() + ":" + expectedQuoteRowVersion)
                .getBytes(StandardCharsets.UTF_8));
        if ("applied".equals(run.applyStatus())) {
            if (!runs.markApplied(runId, applyRequestKey, inputHash, operatorId,
                    objectMapper.valueToTree(Map.of("quoteId", run.quoteId())), time.now())) {
                throw new ServiceException("该任务已用其他请求采用", 409);
            }
            return quotes.get(run.quoteId());
        }
        JsonNode draft = validatedResult(run.output()).path("draft");
        return transaction.execute(status -> {
            QuoteView quote = quotes.applyRequirementSuggestion(
                    run.quoteId(), draft, expectedQuoteRowVersion, operatorId);
            JsonNode applied = objectMapper.valueToTree(Map.of(
                    "quote_id", quote.id(), "quote_row_version", quote.rowVersion(),
                    "requirement_confirmed", quote.requirementConfirmed()));
            if (!runs.markApplied(runId, applyRequestKey, inputHash, operatorId, applied, time.now())) {
                throw new ServiceException("需求建议已被采用或状态变更", 409);
            }
            return quote;
        });
    }

    public ProductView applyProductAttributes(
            String id, String applyRequestKey, long expectedProductRowVersion, long operatorId) {
        requireRequestKey(applyRequestKey);
        long runId = FashionId.parse(id).value();
        RunView run = get(id);
        if (!"succeeded".equals(run.status()) || !"product_attributes".equals(run.outputType())) {
            throw new ServiceException("只有成功的商品属性建议任务可采用");
        }
        JsonNode result = validatedProductResult(run.output());
        long productId = result.path("draft").path("product_ref").asLong(0);
        if (productId <= 0 || productId != run.contextSnapshot().path("product_id").asLong(0)) {
            throw new ServiceException("商品属性建议引用无效");
        }
        Map<String, Object> changes = new java.util.LinkedHashMap<>();
        Map.of("category_code", "categoryCode", "color_code", "colorCode",
                "color_name", "colorName", "season", "season")
                .forEach((source, target) -> {
                    JsonNode value = result.path("draft").path(source);
                    if (value.isTextual() && !value.asText().isBlank()) changes.put(target, value.asText());
                });
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();
        addTaggedValue(tags, result.path("draft"), "style", "style");
        addTaggedValue(tags, result.path("draft"), "scene", "scene");
        addTaggedValue(tags, result.path("draft"), "audience", "audience");
        JsonNode observable = result.path("draft").path("observable_tags");
        if (observable.isArray()) observable.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) tags.add("appearance:" + value.asText().trim());
        });
        if (!tags.isEmpty()) changes.put("tags", List.copyOf(tags));
        String applyHash = FashionHashing.sha256((run.outputHash() + ":" + expectedProductRowVersion)
                .getBytes(StandardCharsets.UTF_8));
        if ("applied".equals(run.applyStatus())) {
            if (!runs.markApplied(runId, applyRequestKey, applyHash, operatorId,
                    objectMapper.valueToTree(Map.of("productId", Long.toString(productId))), time.now())) {
                throw new ServiceException("该任务已用其他请求采用", 409);
            }
            return products.get(Long.toString(productId));
        }
        return transaction.execute(status -> {
            ProductView product = products.applyAttributeSuggestion(Long.toString(productId), changes,
                    expectedProductRowVersion, runId, operatorId);
            if (!runs.markApplied(runId, applyRequestKey, applyHash, operatorId,
                    objectMapper.valueToTree(Map.of("product_id", product.id(),
                            "product_row_version", product.rowVersion(), "attributes_confirmed", true)), time.now())) {
                throw new ServiceException("商品属性建议已被采用或状态变更", 409);
            }
            return product;
        });
    }

    public SelectionApplyResult applySelection(
            String id, String applyRequestKey, long expectedQuoteRowVersion,
            Map<String, String> comboVisualHashes, long operatorId) {
        requireRequestKey(applyRequestKey);
        long runId = FashionId.parse(id).value();
        RunView run = get(id);
        if (!"succeeded".equals(run.status()) || !"selection".equals(run.outputType())) {
            throw new ServiceException("只有成功的选品搭配任务可采用");
        }
        String applyHash = FashionHashing.sha256((run.outputHash() + ":" + expectedQuoteRowVersion + ":"
                + String.valueOf(comboVisualHashes)).getBytes(StandardCharsets.UTF_8));
        if ("applied".equals(run.applyStatus())) {
            if (!runs.markApplied(runId, applyRequestKey, applyHash, operatorId,
                    objectMapper.valueToTree(Map.of("quoteId", run.quoteId())), time.now())) {
                throw new ServiceException("该任务已用其他请求采用", 409);
            }
            return new SelectionApplyResult(run.quoteId(), quotes.get(run.quoteId()).rowVersion(),
                    selection.workspace(run.quoteId()).combinations());
        }
        return transaction.execute(status -> {
            SelectionApplyResult applied = selection.applyRun(
                    run, expectedQuoteRowVersion, comboVisualHashes, operatorId);
            if (!runs.markApplied(runId, applyRequestKey, applyHash, operatorId,
                    objectMapper.valueToTree(applied), time.now())) {
                throw new ServiceException("选品结果已被采用或状态变更", 409);
            }
            return applied;
        });
    }

    /** 单次领取与执行；领取事务在 Runtime 网络调用前已提交。 */
    public boolean processOne(String workerId) {
        if (!runtime.available()) return false;
        Instant claimedAt = time.now();
        int expired = transaction.execute(status -> runs.expireDue(claimedAt));
        telemetry.recordAgentRunEvent("run", "expired", expired);
        String leaseId = UUID.randomUUID().toString();
        RunWorkItem item = transaction.execute(status -> runs.claimNext(
                ids.nextId(), leaseId, workerId, claimedAt, claimedAt.plus(properties.getLeaseDuration())))
                .orElse(null);
        if (item == null) return false;
        String operation = "style".equals(item.triggerType()) ? "selection-styling"
                : "select_products".equals(item.triggerType())
                        ? "product-attribute-suggestion" : "requirement-analysis";
        telemetry.recordAgentRunEvent(operation, "running", 1);
        if (item.runAttempt() > 1) telemetry.recordAgentRunEvent(operation, "lease-takeover", 1);
        CompletableFuture<RequirementRuntimeResult> future = CompletableFuture.supplyAsync(() -> {
            if ("style".equals(item.triggerType())) return runtime.rankSelection(item);
            if ("select_products".equals(item.triggerType())) return runtime.suggestProductAttributes(item);
            return runtime.analyze(item);
        });
        try {
            RequirementRuntimeResult result = waitWithRenewal(item, future);
            boolean productRun = "select_products".equals(item.triggerType());
            boolean selectionRun = "style".equals(item.triggerType());
            JsonNode validated = productRun ? validatedProductResult(result.result())
                    : selectionRun ? validatedSelectionResult(result.result()) : validatedResult(result.result());
            String outputHash = FashionHashing.sha256(write(validated));
            transaction.executeWithoutResult(status -> {
                String outputType = selectionRun ? "selection" : productRun ? "product_attributes" : "requirement";
                String summary = selectionRun ? "选品搭配已完成，等待人工采用"
                        : productRun ? "商品属性建议已完成，等待人工确认" : "需求分析已完成，等待人工确认";
                if (!runs.complete(item, validated, outputType, outputHash, ids.nextId(), summary,
                        time.now())) {
                    throw new FashionRuntimeException("FENCING_CONFLICT", "租约或 fencing token 已失效", false);
                }
            });
            telemetry.recordAgentRunEvent(operation, "succeeded", 1);
            return true;
        } catch (FashionRuntimeException exception) {
            Boolean cancelled = transaction.execute(status -> {
                Instant now = time.now();
                if (runs.acknowledgeCancellation(item, now)) return true;
                runs.fail(item, exception.code(), safeMessage(exception.getMessage()), exception.retryable(), now);
                return false;
            });
            telemetry.recordAgentRunEvent(operation, Boolean.TRUE.equals(cancelled) ? "cancelled" : exception.code(), 1);
            return true;
        } catch (RuntimeException exception) {
            Boolean cancelled = transaction.execute(status -> {
                Instant now = time.now();
                if (runs.acknowledgeCancellation(item, now)) return true;
                runs.fail(item, "INTERNAL_ERROR", "需求分析内部失败", false, now);
                return false;
            });
            telemetry.recordAgentRunEvent(operation, Boolean.TRUE.equals(cancelled) ? "cancelled" : "INTERNAL_ERROR", 1);
            return true;
        } finally {
            future.cancel(true);
        }
    }

    private RequirementRuntimeResult waitWithRenewal(
            RunWorkItem item, CompletableFuture<RequirementRuntimeResult> future) {
        long waitMillis = Math.max(500, Math.min(5000, properties.getLeaseDuration().toMillis() / 2));
        while (true) {
            try {
                return future.get(waitMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                Instant now = time.now();
                if (!now.isBefore(item.deadlineAt())) {
                    throw new FashionRuntimeException("DEADLINE_EXCEEDED", "需求分析超过截止时间", false);
                }
                boolean renewed = transaction.execute(status -> runs.renew(
                        item.runId(), item.leaseId(), item.fencingToken(), now,
                        now.plus(properties.getLeaseDuration())));
                if (!renewed) {
                    throw new FashionRuntimeException("FENCING_CONFLICT", "租约续期被拒绝", false);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FashionRuntimeException("DEPENDENCY_UNAVAILABLE", "Worker 被中断", true);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof FashionRuntimeException runtimeException) throw runtimeException;
                throw new FashionRuntimeException("DEPENDENCY_UNAVAILABLE", "Runtime 调用失败", true);
            }
        }
    }

    private JsonNode validatedProductResult(JsonNode result) {
        if (result == null || !result.isObject()
                || !"product_attributes_only".equals(result.path("fact_scope").asText())
                || !result.path("human_confirmation_required").asBoolean(false)
                || !result.path("draft").isObject()
                || !"pending_human_confirmation".equals(result.path("draft").path("status").asText())
                || !result.path("ambiguities").isArray()) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "Runtime 商品属性输出未通过结构校验", false);
        }
        if (write(result).length > 200_000) {
            throw new FashionRuntimeException("PAYLOAD_TOO_LARGE", "Runtime 商品属性输出超限", false);
        }
        return result;
    }

    private JsonNode validatedSelectionResult(JsonNode result) {
        if (result == null || !result.isObject()
                || !"frozen_candidates_only".equals(result.path("fact_scope").asText())
                || !result.path("human_confirmation_required").asBoolean(false)
                || !result.path("tiers").isArray()
                || result.path("tiers").isEmpty()
                || result.path("tiers").size() > 4) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "Runtime 选品输出未通过契约校验", false);
        }
        return result.deepCopy();
    }

    private JsonNode validatedResult(JsonNode result) {
        if (result == null || !result.isObject()
                || !"requirements_only".equals(result.path("fact_scope").asText())
                || !result.path("human_confirmation_required").asBoolean(false)
                || !result.path("draft").isObject()
                || !"pending_human_confirmation".equals(result.path("draft").path("status").asText())
                || !result.path("unresolved_questions").isArray()) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "Runtime 需求输出未通过结构校验", false);
        }
        if (write(result).length > 200_000) {
            throw new FashionRuntimeException("PAYLOAD_TOO_LARGE", "Runtime 需求输出超限", false);
        }
        return result;
    }

    private byte[] write(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new FashionRuntimeException("REQUEST_VALIDATION_FAILED", "AI 结果无法编码", false);
        }
    }

    private String inputHash(FashionQuote quote, String sourceText) {
        return FashionHashing.sha256((quote.id() + ":" + quote.rowVersion() + ":" + sourceText)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String productAttributeSourceHash(ProductView product) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("productId", product.id());
        snapshot.put("rowVersion", product.rowVersion());
        snapshot.put("sourceCode", product.sourceCode());
        snapshot.put("skuCode", product.skuCode());
        snapshot.put("name", product.name());
        snapshot.put("categoryCode", product.categoryCode());
        snapshot.put("colorCode", product.colorCode());
        snapshot.put("colorName", product.colorName());
        snapshot.put("season", product.season());
        snapshot.put("tags", product.tags());
        return FashionHashing.sha256(write(objectMapper.valueToTree(snapshot)));
    }

    private static void addTaggedValue(List<String> target, JsonNode draft, String field, String prefix) {
        JsonNode value = draft.path(field);
        if (value.isTextual() && !value.asText().isBlank()) target.add(prefix + ":" + value.asText().trim());
    }

    private RunView requireRun(long id) {
        return runs.findById(id).orElseThrow(() -> new ServiceException("Agent Run 不存在"));
    }

    private static void requireRequestKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{15,127}")) {
            throw new ServiceException("requestKey 必须是 16～128 位稳定幂等标识");
        }
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "Runtime 调用失败";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
