package com.ruoyi.fashion.application.quote;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.fashion.application.customer.FashionCustomerService;
import com.ruoyi.fashion.application.quote.port.FashionQuoteRepository;
import com.ruoyi.fashion.application.quote.pricing.port.FashionQuotePricingRepository;
import com.ruoyi.fashion.application.security.FashionDictionaryGuard;
import com.ruoyi.fashion.domain.customer.FashionCustomer;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@FashionModuleEnabled
@Service
public class FashionQuoteDraftService {
    private static final Set<String> BUDGET_BASES = Set.of("total", "per_set");
    private static final Set<String> QUOTE_MODES = Set.of("alternatives", "combined");
    private static final Set<String> STATUSES = Set.of("draft", "confirmed", "void", "closed");

    private final FashionQuoteRepository repository;
    private final FashionCustomerService customers;
    private final FashionDictionaryGuard dictionaries;
    private final FashionIdGenerator ids;
    private final FashionTimeSource time;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final FashionQuotePricingRepository pricing;

    public FashionQuoteDraftService(
            FashionQuoteRepository repository,
            FashionCustomerService customers,
            FashionDictionaryGuard dictionaries,
            FashionIdGenerator ids,
            FashionTimeSource time,
            ObjectMapper objectMapper,
            FashionQuotePricingRepository pricing,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction) {
        this.repository = repository;
        this.customers = customers;
        this.dictionaries = dictionaries;
        this.ids = ids;
        this.time = time;
        this.objectMapper = objectMapper;
        this.pricing = pricing;
        this.transaction = transaction;
    }

    public QuotePage search(String status, String keyword, int page, int pageSize) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new ServiceException("方案状态无效");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(100, Math.max(pageSize, 1));
        long userId = SecurityUtils.getUserId();
        boolean admin = SecurityUtils.isAdmin();
        return new QuotePage(repository.search(userId, admin, trim(status), trim(keyword),
                        (normalizedPage - 1) * normalizedSize, normalizedSize).stream()
                        .map(quote -> QuoteView.from(quote, List.of())).toList(),
                repository.count(userId, admin, trim(status), trim(keyword)), normalizedPage, normalizedSize);
    }

    public QuoteView get(String id) {
        FashionQuote quote = requireAccessible(FashionId.parse(id).value());
        return QuoteView.from(quote, repository.findPriceFacts(quote.id()));
    }

    public QuoteView create(QuoteDraftCommand command, long operatorId) {
        Validated validated = validate(command);
        FashionCustomer customer = requireActiveCustomer(validated.customerId());
        return transaction.execute(status -> {
            long id = ids.nextId();
            Instant now = time.now();
            FashionQuote quote = new FashionQuote(id, "FQ-" + id, 1, null, customer.id(), customer.name(),
                    command.title().trim(), operatorId, trim(command.requirementText()),
                    requirementJson(command.requirements(), false, null, null), false, command.requestedQty(),
                    command.budget(), command.budgetBasis(), command.quoteMode(), command.progressive(),
                    comboTemplate(command.tiers()), command.warehouseCode(), "CNY", "included",
                    presentation(), "draft", operatorId, now, operatorId, now, 1L);
            repository.insert(quote);
            return QuoteView.from(quote, List.of());
        });
    }

    public QuoteView update(String id, QuoteDraftCommand command, long operatorId) {
        Validated validated = validate(command);
        long quoteId = FashionId.parse(id).value();
        FashionQuote current = requireAccessible(quoteId);
        requireDraft(current);
        FashionCustomer customer = requireActiveCustomer(validated.customerId());
        boolean customerChanged = current.customerId() != customer.id();
        if (customerChanged) {
            List<ProductPriceFact> facts = repository.findPriceFacts(quoteId);
            List<String> missing = facts.stream().filter(fact -> !fact.ready()).map(ProductPriceFact::skuCode).toList();
            if (!missing.isEmpty()) {
                throw new ServiceException("切换客户前必须补齐当前销售价：" + String.join(",", missing));
            }
        }
        JsonNode requirement = requirementJson(command.requirements(), false, null, null);
        FashionQuote target = copy(current, customer, command, requirement, comboTemplate(command.tiers()),
                false, operatorId, time.now());
        return transaction.execute(status -> {
            if (!repository.updateDraft(target, command.rowVersion())) {
                throw conflict();
            }
            return get(id);
        });
    }

    public QuoteView confirmRequirements(
            String id, RequirementFields requirements, long expectedRowVersion, long operatorId) {
        long quoteId = FashionId.parse(id).value();
        FashionQuote current = requireAccessible(quoteId);
        requireDraft(current);
        validateRequirements(requirements);
        Instant now = time.now();
        QuoteDraftCommand retained = commandFrom(current, requirements, expectedRowVersion);
        FashionQuote target = copy(current, customers.requireAccessible(current.customerId()), retained,
                requirementJson(requirements, true, operatorId, now), current.comboTemplateJson(),
                true, operatorId, now);
        return transaction.execute(status -> {
            if (!repository.updateDraft(target, expectedRowVersion)) {
                throw conflict();
            }
            return get(id);
        });
    }

    /** 销售明确采用一次成功 Run 的建议时，同时确认需求；AI 原始输出仍只留在 Run。 */
    public QuoteView applyRequirementSuggestion(
            String id, JsonNode draft, long expectedRowVersion, long operatorId) {
        if (draft == null || !draft.isObject()
                || !"pending_human_confirmation".equals(draft.path("status").asText())) {
            throw new ServiceException("AI 需求建议结构无效");
        }
        FashionQuote current = requireAccessible(FashionId.parse(id).value());
        requireDraft(current);
        RequirementFields fields = new RequirementFields(
                text(draft, "audience"), text(draft, "scene"), text(draft, "season"), text(draft, "style"),
                strings(draft.path("preferred_colors"), 20), strings(draft.path("exclusions"), 30),
                date(draft.path("delivery_date")));
        int quantity = draft.path("set_count").isInt() ? draft.path("set_count").asInt() : current.requestedQty();
        String selectionMode = text(draft, "selection_mode");
        boolean progressive = selectionMode == null ? current.progressive() : "progressive".equals(selectionMode);
        List<QuoteTier> tiers = tiersFromSuggestion(draft.path("category_tiers"), current.comboTemplateJson());
        BigDecimal budget = budgetFromSuggestion(draft.path("budget_constraints"), current.budget());
        String budgetBasis = budgetBasisFromSuggestion(draft.path("budget_constraints"), current.budgetBasis());
        String suggestedTitle = text(draft, "scheme_name");
        QuoteDraftCommand command = new QuoteDraftCommand(Long.toString(current.customerId()),
                suggestedTitle == null ? current.title() : suggestedTitle, current.requirementText(), fields,
                quantity, budget, budgetBasis, current.quoteMode(), progressive, tiers,
                current.warehouseCode(), expectedRowVersion);
        validate(command);
        Instant now = time.now();
        FashionQuote target = copy(current, customers.requireAccessible(current.customerId()), command,
                requirementJson(fields, true, operatorId, now), comboTemplate(tiers), true, operatorId, now);
        if (!repository.updateDraft(target, expectedRowVersion)) {
            throw conflict();
        }
        return get(id);
    }

    public QuoteView copyRevision(String id, long operatorId) {
        FashionQuote source = requireAccessible(FashionId.parse(id).value());
        FashionCustomer customer = requireActiveCustomer(source.customerId());
        return transaction.execute(status -> {
            int nextVersion = repository.nextVersion(source.quoteNo());
            long newId = ids.nextId();
            Instant now = time.now();
            FashionQuote copy = new FashionQuote(newId, source.quoteNo(), nextVersion, source.id(), customer.id(),
                    customer.name(), source.title(), operatorId, source.requirementText(), source.requirementJson(),
                    source.requirementConfirmed(), source.requestedQty(), source.budget(), source.budgetBasis(),
                    source.quoteMode(), source.progressive(), source.comboTemplateJson(), source.warehouseCode(),
                    source.currency(), source.taxMode(), source.presentationJson(), "draft", operatorId, now,
                    operatorId, now, 1L);
            repository.insert(copy);
            pricing.copyStructure(source.id(), newId, ids::nextId, operatorId, now);
            return QuoteView.from(copy, List.of());
        });
    }

    public QuoteView close(String id, long expectedRowVersion, long operatorId) {
        long quoteId = FashionId.parse(id).value();
        requireDraft(requireAccessible(quoteId));
        return transaction.execute(status -> {
            if (!repository.updateStatus(quoteId, "closed", expectedRowVersion, operatorId, time.now())) {
                throw conflict();
            }
            return get(id);
        });
    }

    public FashionQuote requireAccessible(long quoteId) {
        FashionQuote quote = repository.findById(quoteId)
                .orElseThrow(() -> new ServiceException("方案不存在或已不可见"));
        customers.requireAccessible(quote.customerId());
        return quote;
    }

    private Validated validate(QuoteDraftCommand command) {
        if (command == null) {
            throw new ServiceException("方案数据不能为空");
        }
        long customerId = FashionId.parse(command.customerId()).value();
        requireText("方案名称", command.title(), 100);
        if (command.requirementText() != null && command.requirementText().length() > 10000) {
            throw new ServiceException("原始需求长度不能超过 10000");
        }
        validateRequirements(command.requirements());
        if (command.requestedQty() < 1 || command.requestedQty() > 100000) {
            throw new ServiceException("采购数量必须为 1～100000 的整数");
        }
        if (command.budget() != null && command.budget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("预算填写后必须大于 0；0 不表示未设预算");
        }
        if (!BUDGET_BASES.contains(command.budgetBasis())) {
            throw new ServiceException("预算口径必须是 total 或 per_set");
        }
        if (!QUOTE_MODES.contains(command.quoteMode())) {
            throw new ServiceException("报价模式必须是 alternatives 或 combined");
        }
        validateTiers(command.tiers(), command.progressive());
        dictionaries.requireActiveValue("fashion_warehouse", command.warehouseCode());
        return new Validated(customerId);
    }

    private void validateTiers(List<QuoteTier> tiers, boolean progressive) {
        if (tiers == null || tiers.isEmpty() || tiers.size() > 4) {
            throw new ServiceException("必须选择 1～4 个品类档位");
        }
        int previous = 0;
        List<String> previousSlots = List.of();
        for (QuoteTier tier : tiers) {
            if (tier.count() < 1 || tier.count() > 4 || tier.count() <= previous) {
                throw new ServiceException("品类档位 count 必须在 1～4 内严格升序且不重复");
            }
            if (tier.candidateCount() < 1 || tier.candidateCount() > 3) {
                throw new ServiceException("每档候选数必须为 1～3");
            }
            if (tier.slots() == null || tier.slots().size() != tier.count()
                    || new HashSet<>(tier.slots()).size() != tier.slots().size()) {
                throw new ServiceException("档位槽位数必须等于 count 且品类不重复");
            }
            tier.slots().forEach(slot -> dictionaries.requireActiveValue("fashion_product_category", slot));
            if (progressive && !tier.slots().subList(0, previous).equals(previousSlots)) {
                throw new ServiceException("递进模式后续档位必须继承前一档槽位");
            }
            previous = tier.count();
            previousSlots = List.copyOf(tier.slots());
        }
    }

    private static void validateRequirements(RequirementFields requirements) {
        if (requirements == null) {
            return;
        }
        requireOptional("目标人群", requirements.audience(), 120);
        requireOptional("场景", requirements.scene(), 120);
        requireOptional("季节", requirements.season(), 120);
        requireOptional("风格", requirements.style(), 120);
        requireList("颜色偏好", requirements.preferredColors(), 20);
        requireList("禁止项", requirements.exclusions(), 30);
        if (requirements.deliveryDate() != null && requirements.deliveryDate().isBefore(LocalDate.now())) {
            throw new ServiceException("交付日期不能早于今天");
        }
    }

    private JsonNode requirementJson(RequirementFields fields, boolean confirmed, Long confirmedBy, Instant confirmedAt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schema_version", "1.0");
        node.put("confirmed", confirmed);
        if (confirmedBy == null) node.putNull("confirmed_by"); else node.put("confirmed_by", Long.toString(confirmedBy));
        if (confirmedAt == null) node.putNull("confirmed_at"); else node.put("confirmed_at", confirmedAt.toString());
        RequirementFields safe = fields == null ? new RequirementFields(null, null, null, null, List.of(), List.of(), null) : fields;
        putNullable(node, "audience", safe.audience());
        putNullable(node, "scene", safe.scene());
        putNullable(node, "season", safe.season());
        putNullable(node, "style", safe.style());
        node.set("preferred_colors", objectMapper.valueToTree(safe.preferredColors() == null ? List.of() : safe.preferredColors()));
        node.set("exclusions", objectMapper.valueToTree(safe.exclusions() == null ? List.of() : safe.exclusions()));
        putNullable(node, "delivery_date", safe.deliveryDate() == null ? null : safe.deliveryDate().toString());
        return node;
    }

    private JsonNode comboTemplate(List<QuoteTier> tiers) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schema_version", "1.0");
        node.put("template_code", "quote-tier-selection");
        node.put("template_version", "1.0");
        ArrayNode groups = node.putArray("groups");
        tiers.forEach(tier -> {
            ObjectNode group = groups.addObject();
            group.put("count", tier.count());
            group.set("slots", objectMapper.valueToTree(tier.slots()));
            group.put("candidate_count", tier.candidateCount());
        });
        return node;
    }

    private JsonNode presentation() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schema_version", "1.0");
        node.put("layout_version", "1.0");
        return node;
    }

    private static FashionQuote copy(
            FashionQuote current, FashionCustomer customer, QuoteDraftCommand command, JsonNode requirement,
            JsonNode comboTemplate, boolean confirmed, long operatorId, Instant now) {
        return new FashionQuote(current.id(), current.quoteNo(), current.versionNo(), current.sourceQuoteId(),
                customer.id(), customer.name(), command.title().trim(), current.salespersonId(),
                trim(command.requirementText()), requirement, confirmed, command.requestedQty(), command.budget(),
                command.budgetBasis(), command.quoteMode(), command.progressive(), comboTemplate.deepCopy(),
                command.warehouseCode(), "CNY", "included",
                current.presentationJson(), current.status(), current.createBy(), current.createTime(), operatorId,
                now, current.rowVersion() + 1);
    }

    private QuoteDraftCommand commandFrom(
            FashionQuote quote, RequirementFields requirements, long expectedRowVersion) {
        List<QuoteTier> tiers = new ArrayList<>();
        quote.comboTemplateJson().path("groups").forEach(group -> {
            List<String> slots = new ArrayList<>();
            group.path("slots").forEach(slot -> slots.add(slot.asText()));
            tiers.add(new QuoteTier(group.path("count").asInt(), slots, group.path("candidate_count").asInt()));
        });
        return new QuoteDraftCommand(Long.toString(quote.customerId()), quote.title(), quote.requirementText(),
                requirements, quote.requestedQty(), quote.budget(), quote.budgetBasis(), quote.quoteMode(),
                quote.progressive(), tiers, quote.warehouseCode(), expectedRowVersion);
    }

    private List<QuoteTier> tiersFromSuggestion(JsonNode source, JsonNode fallback) {
        if (!source.isArray() || source.isEmpty()) {
            ArrayList<QuoteTier> result = new ArrayList<>();
            fallback.path("groups").forEach(group -> {
                List<String> slots = strings(group.path("slots"), 4);
                result.add(new QuoteTier(group.path("count").asInt(), slots,
                        group.path("candidate_count").asInt()));
            });
            return result;
        }
        ArrayList<QuoteTier> result = new ArrayList<>();
        source.forEach(tier -> {
            ArrayList<String> slots = new ArrayList<>();
            tier.path("slots").forEach(slot -> slots.add(slot.path("category").asText()));
            result.add(new QuoteTier(tier.path("category_count").asInt(), slots,
                    tier.path("candidate_count").asInt()));
        });
        return result;
    }

    private static BigDecimal budgetFromSuggestion(JsonNode source, BigDecimal fallback) {
        if (!source.isArray()) return fallback;
        for (JsonNode value : source) {
            if (("total".equals(value.path("basis").asText()) || "per_set".equals(value.path("basis").asText()))
                    && value.path("maximum_minor").canConvertToLong()) {
                return BigDecimal.valueOf(value.path("maximum_minor").asLong(), 2);
            }
        }
        return fallback;
    }

    private static String budgetBasisFromSuggestion(JsonNode source, String fallback) {
        if (!source.isArray()) return fallback;
        for (JsonNode value : source) {
            String basis = value.path("basis").asText();
            if (BUDGET_BASES.contains(basis)) return basis;
        }
        return fallback;
    }

    private static List<String> strings(JsonNode source, int maximum) {
        if (!source.isArray() || source.size() > maximum) {
            if (source.isMissingNode() || source.isNull()) return List.of();
            throw new ServiceException("AI 需求建议数组结构无效");
        }
        ArrayList<String> result = new ArrayList<>();
        source.forEach(value -> {
            if (!value.isTextual()) throw new ServiceException("AI 需求建议文本结构无效");
            result.add(value.asText());
        });
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static LocalDate date(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw new ServiceException("AI 交付日期格式无效");
        try {
            return LocalDate.parse(value.asText());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ServiceException("AI 交付日期格式无效");
        }
    }

    private FashionCustomer requireActiveCustomer(long customerId) {
        FashionCustomer customer = customers.requireAccessible(customerId);
        if (!"active".equals(customer.status())) {
            throw new ServiceException("已归档客户不能新建或切换方案");
        }
        return customer;
    }

    private static void requireDraft(FashionQuote quote) {
        if (!"draft".equals(quote.status())) {
            throw new ServiceException("只有草稿方案可修改或关闭");
        }
    }

    private static void requireText(String field, String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ServiceException(field + "必须为 1～" + max + " 个字符");
        }
    }

    private static void requireOptional(String field, String value, int max) {
        if (value != null && value.trim().length() > max) {
            throw new ServiceException(field + "长度不能超过 " + max);
        }
    }

    private static void requireList(String field, List<String> values, int maxItems) {
        if (values == null) return;
        if (values.size() > maxItems || new HashSet<>(values).size() != values.size()) {
            throw new ServiceException(field + "数量超限或存在重复项");
        }
        values.forEach(value -> requireText(field, value, 120));
    }

    private static void putNullable(ObjectNode node, String name, String value) {
        if (value == null || value.isBlank()) node.putNull(name); else node.put(name, value.trim());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ServiceException conflict() {
        return new ServiceException("方案已被其他操作修改，请刷新后重试", 409);
    }

    private record Validated(long customerId) {
    }
}
