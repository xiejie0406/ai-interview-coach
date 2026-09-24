package com.ruoyi.fashion.application.quote.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Calculation;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Combo;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Line;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingCalculator.Request;
import org.junit.jupiter.api.Test;

class QuotePricingCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private final QuotePricingCalculator calculator = new QuotePricingCalculator();

    @Test
    void exactIncludedExamplesUseDecimalAndRoundOnlyAtAccountingLayer() {
        Calculation fourCategories = calculator.calculate(request("combined", "included", null, "percent",
                "5.00", "0", "300.00", combo(1, 100, line(1, "top", 1, 100, "80.00", 100),
                        line(2, "pants", 2, 100, "60.00", 100), line(3, "hat", 3, 100, "20.00", 130),
                        line(4, "shoes", 4, 100, "90.00", 100))), NOW, 24);
        Calculation fractional = calculator.calculate(request("combined", "included", null, "percent",
                "7.50", "0", "0.99", combo(2, 3, line(5, "top", 5, 3, "19.99", 3))), NOW, 24);

        assertThat(fourCategories.subtotal()).isEqualByComparingTo("25000.00");
        assertThat(fourCategories.discount()).isEqualByComparingTo("1250.00");
        assertThat(fourCategories.total()).isEqualByComparingTo("24050.00");
        assertThat(fourCategories.maximumAvailableSets()).isEqualTo(100);
        assertThat(fractional.subtotal()).isEqualByComparingTo("59.97");
        assertThat(fractional.discount()).isEqualByComparingTo("4.50");
        assertThat(fractional.total()).isEqualByComparingTo("56.46");
    }

    @Test
    void excludedTaxAddsTaxOnceAndIncludedModeRejectsASecondTaxRate() {
        Calculation excluded = calculator.calculate(request("combined", "excluded", "13.00", "percent",
                "5.00", "0", "10.00", combo(1, 1, excludedLine(1, "top", 1, 1, "100.00", 5))), NOW, 24);
        Calculation included = calculator.calculate(request("combined", "included", null, "percent",
                "5.00", "0", "10.00", combo(1, 1, includedLine(1, "top", 1, 1, "100.00", 5))), NOW, 24);

        assertThat(excluded.tax()).isEqualByComparingTo("13.65");
        assertThat(excluded.total()).isEqualByComparingTo("118.65");
        assertThat(included.tax()).isEqualByComparingTo("0.00");
        assertThat(included.total()).isEqualByComparingTo("105.00");
        assertThatThrownBy(() -> calculator.calculate(request("combined", "included", "13.00", "percent",
                "5.00", "0", "10.00", combo(1, 1, includedLine(1, "top", 1, 1, "100.00", 5))), NOW, 24))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不能重复填写");
    }

    @Test
    void alternativesDoNotAddCandidateTotalsOrDoubleConsumeSharedStock() {
        Combo first = combo(1, 60, line(1, "hat", 9, 60, "20.00", 100));
        Combo second = combo(2, 50, line(2, "hat", 9, 50, "20.00", 100));
        Calculation alternatives = calculator.calculate(request("alternatives", "included", null, "percent",
                "5.00", "0", "0.00", first, second), NOW, 24);
        Calculation combined = calculator.calculate(request("combined", "included", null, "percent",
                "5.00", "0", "0.00", first, second), NOW, 24);

        assertThat(alternatives.total()).isNull();
        assertThat(alternatives.combos()).extracting(it -> it.total().toPlainString())
                .containsExactly("1140.00", "950.00");
        assertThat(alternatives.combos()).extracting(it -> it.maximumAvailableSets())
                .containsExactly(100, 100);
        assertThat(combined.maximumAvailableSets()).isEqualTo(100);
        assertThat(alternatives.issues()).noneMatch(issue -> issue.code().equals("STOCK_SHORTAGE"));
        assertThat(combined.issues()).anyMatch(issue -> issue.code().equals("STOCK_SHORTAGE")
                && issue.message().contains("需要 110") && issue.message().contains("短缺 10"));
    }

    @Test
    void allocationMustEqualSetQuantityForEverySlot() {
        Calculation over = calculator.calculate(request("combined", "included", null, "percent", "5.00", "0",
                "0.00", combo(1, 100, line(1, "top", 1, 60, "80.00", 100),
                        line(2, "top", 2, 50, "80.00", 100))), NOW, 24);
        Calculation under = calculator.calculate(request("combined", "included", null, "percent", "5.00", "0",
                "0.00", combo(1, 100, line(1, "top", 1, 60, "80.00", 100),
                        line(2, "top", 2, 30, "80.00", 100))), NOW, 24);

        assertThat(over.issues()).anyMatch(issue -> issue.code().equals("ALLOCATION_MISMATCH")
                && issue.message().contains("110"));
        assertThat(under.issues()).anyMatch(issue -> issue.code().equals("ALLOCATION_MISMATCH")
                && issue.message().contains("90"));
    }

    @Test
    void approvalCannotMakeZeroPriceOrZeroPayableAFormalQuote() {
        Calculation zeroPrice = calculator.calculate(request("combined", "included", null, "percent", "0.00", "0",
                "0.00", combo(1, 1, line(1, "top", 1, 1, "0.00", 1))), NOW, 24);
        Calculation fullDiscount = calculator.calculate(request("combined", "included", null, "percent", "100.00", "0",
                "0.00", combo(1, 1, line(1, "top", 1, 1, "100.00", 1))), NOW, 24);

        assertThat(zeroPrice.issues()).anyMatch(issue -> issue.code().equals("NON_POSITIVE_PRICE"));
        assertThat(fullDiscount.approvalRequired()).isTrue();
        assertThat(fullDiscount.issues()).anyMatch(issue -> issue.code().equals("ZERO_PAYABLE"));
        Calculation fixedFullDiscount = calculator.calculate(request("combined", "included", null, "fixed",
                "0.00", "100.00", "10.00", combo(1, 1, line(1, "top", 1, 1, "100.00", 1))), NOW, 24);
        assertThat(fixedFullDiscount.total()).isEqualByComparingTo("10.00");
        assertThat(fixedFullDiscount.issues()).anyMatch(issue -> issue.code().equals("ZERO_PAYABLE"));
    }

    @Test
    void fixedDiscountUsesEquivalentRateAndPrecisionIsNeverSilentlyRounded() {
        Calculation fixed = calculator.calculate(request("combined", "included", null, "fixed", "0", "11.00",
                "0.00", combo(1, 1, line(1, "top", 1, 1, "100.00", 2))), NOW, 24);
        assertThat(fixed.approvalRequired()).isTrue();
        assertThatThrownBy(() -> calculator.calculate(request("combined", "included", null, "percent", "5.00", "0",
                "0.00", combo(1, 1, line(1, "top", 1, 1, "19.995", 2))), NOW, 24))
                .isInstanceOf(ServiceException.class).hasMessageContaining("最多 2 位小数");

        Calculation alternatives = calculator.calculate(request("alternatives", "included", null, "fixed", "0",
                "11.00", "0.00", combo(1, 1, line(1, "top", 1, 1, "100.00", 2)),
                combo(2, 1, line(2, "top", 2, 1, "100.00", 2))), NOW, 24);
        assertThat(alternatives.approvalRequired()).isTrue();
    }

    @Test
    void anEntireMissingSlotIsAlsoAnAllocationFailure() {
        Combo missing = new Combo(1, 2, 100, true, List.of(line(1, "top", 1, 100, "80.00", 100)));
        Calculation result = calculator.calculate(request("combined", "included", null, "percent", "5.00", "0",
                "0.00", missing), NOW, 24);
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("MISSING_SLOT"));
    }

    @Test
    void staleOrMixedFactsProduceLocatableBlockingIssues() {
        Line stale = new Line(1, "top", 1, "SKU-1", 1, new BigDecimal("10.00"), new BigDecimal("10.00"),
                "USD", "included", NOW.minusSeconds(3600), 10L, 5, NOW.minusSeconds(25 * 3600), 20L, false);
        Calculation result = calculator.calculate(request("combined", "excluded", "0.00", "percent", "0.00", "0",
                "0.00", combo(1, 1, stale)), NOW, 24);

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("CURRENCY_MISMATCH", "TAX_MODE_MISMATCH", "PRODUCT_INACTIVE", "STOCK_STALE");
    }

    @Test
    void theSameSkuCannotCarryDifferentStockFactsAcrossCombinedCombos() {
        Line first = line(1, "top", 1, 40, "10.00", 100);
        Line changed = new Line(2, "top", 1, "SKU-1", 60, new BigDecimal("10.00"),
                new BigDecimal("10.00"), "CNY", "included", NOW.minusSeconds(3600), 10L,
                90, NOW.minusSeconds(3600), 21L, true);
        Calculation result = calculator.calculate(request("combined", "included", null, "percent",
                "0.00", "0", "0", combo(1, 40, first), combo(2, 60, changed)), NOW, 24);

        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("INCONSISTENT_PRODUCT_FACT"));
    }

    private static Request request(String mode, String taxMode, String taxRate, String type, String rate,
            String fixed, String freight, Combo... combos) {
        return new Request(mode, taxMode, taxRate == null ? null : new BigDecimal(taxRate), true, type,
                new BigDecimal(rate), new BigDecimal(fixed), new BigDecimal(freight), List.of(combos));
    }
    private static Combo combo(long id, int sets, Line... lines) {
        int categories = (int) java.util.Arrays.stream(lines).map(Line::slotCode).distinct().count();
        return new Combo(id, categories, sets, true, List.of(lines));
    }
    private static Line line(long id, String slot, long product, int qty, String price, int stock) {
        return fact(id, slot, product, qty, price, stock, "included");
    }
    private static Line includedLine(long id, String slot, long product, int qty, String price, int stock) {
        return fact(id, slot, product, qty, price, stock, "included");
    }
    private static Line excludedLine(long id, String slot, long product, int qty, String price, int stock) {
        return fact(id, slot, product, qty, price, stock, "excluded");
    }
    private static Line fact(long id, String slot, long product, int qty, String price, int stock, String taxMode) {
        BigDecimal amount = new BigDecimal(price);
        return new Line(id, slot, product, "SKU-" + product, qty, amount, amount, "CNY", taxMode,
                NOW.minusSeconds(3600), 10L, stock, NOW.minusSeconds(3600), 20L, true);
    }
}
