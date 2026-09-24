package com.ruoyi.aps.domain.quantity;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket;
import com.ruoyi.aps.domain.quantity.QuantityLedger.EventType;
import com.ruoyi.aps.domain.quantity.QuantityLedger.Movement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityLedgerTest
{
    @Test
    void validatesReportBalanceAndQualitySubsets()
    {
        var quantities = new ProductionReportQuantities(q("100"), q("80"), q("15"), q("5"), q("4"), q("20"));
        quantities.requirePositiveUnlessCorrection(false);

        assertThatThrownBy(() -> new ProductionReportQuantities(q("99"), q("80"), q("15"), q("5"),
                q("4"), q("20"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("必须等于");
        assertThatThrownBy(() -> new ProductionReportQuantities(q("100"), q("80"), q("15"), q("5"),
                q("6"), q("20"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scrapQty");
    }

    @Test
    void keepsPendingQuantityUnavailableUntilQualityRelease()
    {
        var ledger = new QuantityLedger().apply(List.of(
                Movement.produce(q("80")),
                new Movement(EventType.QUALITY_RELEASE, Bucket.PENDING_QUALITY, Bucket.AVAILABLE, q("80")),
                Movement.produce(q("20"))));

        assertThat(ledger.balance(Bucket.AVAILABLE)).isEqualByComparingTo("80");
        assertThat(ledger.balance(Bucket.PENDING_QUALITY)).isEqualByComparingTo("20");
    }

    @Test
    void reversesNullableProduceEndpointExactly()
    {
        Movement produce = Movement.produce(q("20"));
        Movement reverse = Movement.reverse(produce);
        reverse.requireExactReversalOf(produce);

        assertThat(reverse.fromBucket()).isEqualTo(Bucket.PENDING_QUALITY);
        assertThat(reverse.toBucket()).isNull();
        assertThat(new QuantityLedger().apply(produce).apply(reverse).balance(Bucket.PENDING_QUALITY))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsInvalidShapeAndNegativeProjection()
    {
        assertThatThrownBy(() -> new Movement(EventType.PRODUCE, Bucket.AVAILABLE,
                Bucket.PENDING_QUALITY, q("1"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("桶形状");
        assertThatThrownBy(() -> new QuantityLedger().apply(
                new Movement(EventType.QUALITY_RELEASE, Bucket.PENDING_QUALITY, Bucket.AVAILABLE, q("1"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不能为负数");
    }

    private static BigDecimal q(String value)
    {
        return new BigDecimal(value);
    }
}
