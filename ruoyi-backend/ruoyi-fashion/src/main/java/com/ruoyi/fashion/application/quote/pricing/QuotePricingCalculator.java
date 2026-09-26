package com.ruoyi.fashion.application.quote.pricing;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.stereotype.Component;

/** 报价的唯一十进制计算内核；不读取网络、数据库或 AI 输出。 */
@FashionModuleEnabled
@Component
public final class QuotePricingCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999999999.99");
    private static final Set<String> MODES = Set.of("alternatives", "combined");
    private static final Set<String> TAX_MODES = Set.of("included", "excluded");
    private static final Set<String> DISCOUNT_TYPES = Set.of("percent", "fixed");

    public Calculation calculate(Request request, Instant now, int freshnessHours) {
        validate(request, freshnessHours);
        List<Issue> issues = new ArrayList<>();
        List<ComboResult> combos = new ArrayList<>();
        List<Combo> selected = request.combos().stream().filter(Combo::selected).toList();
        if (selected.isEmpty()) issues.add(issue("EMPTY_SELECTION", "至少选择一个报价组合"));

        selected.forEach(combo -> validateAllocation(combo, issues));
        validateFacts(selected, request, now, freshnessHours, issues);
        validateConsistentProductFacts(selected, issues);
        validateStock(selected, request.mode(), issues);

        if ("alternatives".equals(request.mode())) {
            for (Combo combo : selected) {
                Money money = money(combo.lines(), request);
                combos.add(new ComboResult(combo.id(), money.subtotal(), money.discount(), request.freight(),
                        money.tax(), money.total(), average(money.total(), combo.setQty()),
                        maximumAvailableSets(combo.lines(), combo.setQty())));
                formalMoneyIssues(money, combo.id(), request, issues);
            }
            return new Calculation(null, null, null, null, null, null, List.copyOf(combos), List.copyOf(issues),
                    requiresApproval(selected, request));
        }

        List<Line> allLines = selected.stream().flatMap(combo -> combo.lines().stream()).toList();
        Money money = money(allLines, request);
        formalMoneyIssues(money, null, request, issues);
        int nominalSets = selected.stream().mapToInt(Combo::setQty).sum();
        return new Calculation(money.subtotal(), money.discount(), request.freight(), money.tax(), money.total(),
                maximumAvailableSets(allLines, nominalSets), List.of(), List.copyOf(issues),
                requiresApproval(selected, request));
    }

    private static Money money(List<Line> lines, Request request) {
        BigDecimal subtotal = lines.stream().map(line -> amount(line.unitPrice(), line.qty()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal discount = "percent".equals(request.discountType())
                ? money(subtotal.multiply(request.discountRate()).divide(HUNDRED, 8, RoundingMode.HALF_UP))
                : money(request.fixedDiscount());
        if (discount.compareTo(subtotal) > 0) throw new ServiceException("固定优惠不能超过商品合计");
        BigDecimal taxableBase = subtotal.subtract(discount)
                .add(request.feeTaxable() ? request.freight() : BigDecimal.ZERO);
        BigDecimal tax = "excluded".equals(request.taxMode())
                ? money(taxableBase.multiply(request.taxRate()).divide(HUNDRED, 8, RoundingMode.HALF_UP))
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = money(subtotal.subtract(discount).add(request.freight()).add(tax));
        moneyRange("商品合计", subtotal); moneyRange("折扣金额", discount);
        moneyRange("税额", tax); moneyRange("应付总额", total);
        return new Money(subtotal, discount, tax, total);
    }

    private static void validateAllocation(Combo combo, List<Issue> issues) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        Set<String> unique = new LinkedHashSet<>();
        if (combo.categoryCount() < 1 || combo.categoryCount() > 4
                || combo.setQty() < 1 || combo.setQty() > 100000) {
            throw new ServiceException("组合品类数或采购套数超出首期范围");
        }
        for (Line line : combo.lines()) {
            if (line.qty() <= 0 || line.qty() > 100000) throw new ServiceException("尺码数量必须是 1～100000 的整数");
            String key = line.slotCode() + "\u0000" + line.productId();
            if (!unique.add(key)) throw new ServiceException("同一槽位不能重复同一 SKU");
            quantities.merge(line.slotCode(), line.qty(), Integer::sum);
        }
        if (quantities.size() != combo.categoryCount()) issues.add(issue("MISSING_SLOT",
                "组合 " + combo.id() + " 应包含 " + combo.categoryCount() + " 个槽位，当前为 " + quantities.size()));
        if (quantities.isEmpty()) issues.add(issue("EMPTY_ALLOCATION", "组合 " + combo.id() + " 没有尺码分配"));
        quantities.forEach((slot, qty) -> {
            if (qty != combo.setQty()) issues.add(issue("ALLOCATION_MISMATCH",
                    "组合 " + combo.id() + " 的槽位 " + slot + " 数量 " + qty + "，应为 " + combo.setQty()));
        });
    }

    private static void validateFacts(
            List<Combo> combos, Request request, Instant now, int freshnessHours, List<Issue> issues) {
        for (Combo combo : combos) for (Line line : combo.lines()) {
            precision("报价单价", line.unitPrice(), 2);
            moneyRange("报价单价", line.unitPrice());
            if (line.unitPrice().signum() <= 0) issues.add(issue("NON_POSITIVE_PRICE", "SKU " + line.skuCode() + " 单价必须大于 0"));
            if (!"CNY".equals(line.currency())) issues.add(issue("CURRENCY_MISMATCH", "SKU " + line.skuCode() + " 不是 CNY"));
            if (!request.taxMode().equals(line.taxMode())) issues.add(issue("TAX_MODE_MISMATCH", "SKU " + line.skuCode() + " 税口径不一致"));
            if (!line.active()) issues.add(issue("PRODUCT_INACTIVE", "SKU " + line.skuCode() + " 已不可用"));
            if (line.priceBatchId() == null || line.priceAsOf() == null) issues.add(issue("PRICE_UNKNOWN", "SKU " + line.skuCode() + " 缺少价格来源"));
            if (line.stockBatchId() == null || line.stockAsOf() == null) {
                issues.add(issue("STOCK_UNKNOWN", "SKU " + line.skuCode() + " 缺少库存核实来源"));
            } else if (line.stockAsOf().isAfter(now)
                    || Duration.between(line.stockAsOf(), now).compareTo(Duration.ofHours(freshnessHours)) > 0) {
                issues.add(issue("STOCK_STALE", "SKU " + line.skuCode() + " 库存已超过 " + freshnessHours + " 小时"));
            }
        }
    }

    private static void validateStock(List<Combo> combos, String mode, List<Issue> issues) {
        if ("combined".equals(mode)) {
            Map<Long, Integer> required = new LinkedHashMap<>();
            Map<Long, Line> facts = new LinkedHashMap<>();
            combos.forEach(combo -> combo.lines().forEach(line -> {
                required.merge(line.productId(), line.qty(), Integer::sum); facts.putIfAbsent(line.productId(), line);
            }));
            required.forEach((productId, qty) -> stockIssue(facts.get(productId), qty, issues, "合并采购"));
            return;
        }
        combos.forEach(combo -> {
            Map<Long, Integer> required = new LinkedHashMap<>();
            Map<Long, Line> facts = new LinkedHashMap<>();
            combo.lines().forEach(line -> {
                required.merge(line.productId(), line.qty(), Integer::sum); facts.putIfAbsent(line.productId(), line);
            });
            required.forEach((productId, qty) -> stockIssue(facts.get(productId), qty, issues, "组合 " + combo.id()));
        });
    }

    private static void validateConsistentProductFacts(List<Combo> combos, List<Issue> issues) {
        Map<Long, Line> first = new LinkedHashMap<>();
        combos.stream().flatMap(combo -> combo.lines().stream()).forEach(line -> {
            Line previous = first.putIfAbsent(line.productId(), line);
            if (previous != null && (!sameNumber(previous.sourcePrice(), line.sourcePrice())
                    || !same(previous.currency(), line.currency()) || !same(previous.taxMode(), line.taxMode())
                    || !same(previous.priceBatchId(), line.priceBatchId())
                    || !same(previous.priceAsOf(), line.priceAsOf()) || !same(previous.stockQty(), line.stockQty())
                    || !same(previous.stockBatchId(), line.stockBatchId())
                    || !same(previous.stockAsOf(), line.stockAsOf()))) {
                issues.add(issue("INCONSISTENT_PRODUCT_FACT", "SKU " + line.skuCode() + " 在组合间的价格或库存事实不一致"));
            }
        });
    }

    private static Integer maximumAvailableSets(List<Line> lines, int nominalSets) {
        if (lines.isEmpty() || nominalSets <= 0 || lines.stream().anyMatch(line -> line.stockQty() == null)) return null;
        Map<Long, Integer> required = new LinkedHashMap<>();
        Map<Long, Integer> stock = new LinkedHashMap<>();
        lines.forEach(line -> {
            required.merge(line.productId(), line.qty(), Integer::sum);
            stock.putIfAbsent(line.productId(), line.stockQty());
        });
        long maximum = Integer.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : required.entrySet()) {
            long candidate = (long) stock.get(entry.getKey()) * nominalSets / entry.getValue();
            maximum = Math.min(maximum, candidate);
        }
        return (int) Math.max(0, maximum);
    }

    private static void stockIssue(Line line, int required, List<Issue> issues, String scope) {
        if (line.stockQty() == null) return;
        if (required > line.stockQty()) issues.add(issue("STOCK_SHORTAGE", scope + " 的 SKU " + line.skuCode()
                + " 需要 " + required + "，可售 " + line.stockQty() + "，短缺 " + (required - line.stockQty())));
    }

    private static boolean requiresApproval(List<Combo> combos, Request request) {
        boolean priceOverride = combos.stream().flatMap(combo -> combo.lines().stream())
                .anyMatch(line -> line.sourcePrice() == null || line.unitPrice().compareTo(line.sourcePrice()) != 0);
        boolean excessive;
        if ("percent".equals(request.discountType())) {
            excessive = request.discountRate().compareTo(new BigDecimal("10.00")) > 0;
        } else if ("alternatives".equals(request.mode())) {
            excessive = combos.stream().anyMatch(combo -> fixedExceedsLimit(combo.lines(), request.fixedDiscount()));
        } else {
            excessive = fixedExceedsLimit(combos.stream().flatMap(combo -> combo.lines().stream()).toList(),
                    request.fixedDiscount());
        }
        return priceOverride || excessive;
    }

    private static boolean fixedExceedsLimit(List<Line> lines, BigDecimal fixedDiscount) {
        BigDecimal subtotal = lines.stream().map(line -> amount(line.unitPrice(), line.qty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.signum() > 0 && fixedDiscount.multiply(HUNDRED)
                .compareTo(subtotal.multiply(new BigDecimal("10.00"))) > 0;
    }

    private static void formalMoneyIssues(Money money, Long comboId, Request request, List<Issue> issues) {
        String scope = comboId == null ? "合并采购" : "组合 " + comboId;
        if (money.subtotal().signum() <= 0) issues.add(issue("ZERO_SUBTOTAL", scope + " 商品合计必须大于 0"));
        if (money.discount().compareTo(money.subtotal()) == 0 || money.total().signum() <= 0) {
            issues.add(issue("ZERO_PAYABLE", scope + " 的 100% 优惠或零应付不能正式确认"));
        }
    }

    private static void validate(Request request, int freshnessHours) {
        if (request == null || !MODES.contains(request.mode()) || !TAX_MODES.contains(request.taxMode())
                || !DISCOUNT_TYPES.contains(request.discountType())) throw new ServiceException("报价模式参数无效");
        if (freshnessHours < 1 || freshnessHours > 168) throw new ServiceException("库存新鲜度配置无效");
        precision("折扣率", request.discountRate(), 2); precision("固定优惠", request.fixedDiscount(), 2);
        precision("运费", request.freight(), 2);
        moneyRange("固定优惠", request.fixedDiscount()); moneyRange("运费", request.freight());
        if (request.discountRate().signum() < 0 || request.discountRate().compareTo(HUNDRED) > 0
                || request.fixedDiscount().signum() < 0 || request.freight().signum() < 0) {
            throw new ServiceException("折扣和费用不能为负，折扣率不能超过 100%");
        }
        if ("percent".equals(request.discountType()) && request.fixedDiscount().signum() != 0) {
            throw new ServiceException("百分比优惠不能同时填写固定优惠");
        }
        if ("fixed".equals(request.discountType()) && request.discountRate().signum() != 0) {
            throw new ServiceException("固定优惠不能同时填写折扣率");
        }
        if ("included".equals(request.taxMode())) {
            if (request.taxRate() != null) throw new ServiceException("含税模式不能重复填写加收税率");
        } else {
            if (request.taxRate() == null) throw new ServiceException("未税模式必须明确填写税率，0 也需显式填写");
            precision("税率", request.taxRate(), 2);
            if (request.taxRate().signum() < 0 || request.taxRate().compareTo(HUNDRED) > 0) {
                throw new ServiceException("税率必须在 0～100% 之间");
            }
        }
        if (request.combos() == null || request.combos().size() > 100) throw new ServiceException("报价组合数量无效");
    }

    private static void precision(String field, BigDecimal value, int scale) {
        if (value == null || value.scale() > scale) throw new ServiceException(field + "最多 " + scale + " 位小数");
    }
    private static void moneyRange(String field, BigDecimal value) {
        if (value == null || value.abs().compareTo(MAX_MONEY) > 0) {
            throw new ServiceException(field + "超出 DECIMAL(16,2) 范围");
        }
    }

    private static BigDecimal amount(BigDecimal price, int qty) { return money(price.multiply(BigDecimal.valueOf(qty))); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal average(BigDecimal total, int qty) {
        return qty <= 0 ? null : total.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
    }
    private static boolean same(Object left, Object right) { return java.util.Objects.equals(left, right); }
    private static boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
    private static Issue issue(String code, String message) { return new Issue(code, message); }

    public record Request(String mode, String taxMode, BigDecimal taxRate, boolean feeTaxable,
            String discountType, BigDecimal discountRate, BigDecimal fixedDiscount, BigDecimal freight,
            List<Combo> combos) {}
    public record Combo(long id, int categoryCount, int setQty, boolean selected, List<Line> lines) {}
    public record Line(long id, String slotCode, long productId, String skuCode, int qty, BigDecimal unitPrice,
            BigDecimal sourcePrice, String currency, String taxMode, Instant priceAsOf, Long priceBatchId,
            Integer stockQty, Instant stockAsOf, Long stockBatchId, boolean active) {}
    public record Calculation(BigDecimal subtotal, BigDecimal discount, BigDecimal freight, BigDecimal tax,
            BigDecimal total, Integer maximumAvailableSets, List<ComboResult> combos,
            List<Issue> issues, boolean approvalRequired) {
        public boolean confirmable() { return issues.isEmpty(); }
    }
    public record ComboResult(long id, BigDecimal subtotal, BigDecimal discount, BigDecimal freight,
            BigDecimal tax, BigDecimal total, BigDecimal averagePerSet, Integer maximumAvailableSets) {}
    public record Issue(String code, String message) {}
    private record Money(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {}
}
