package com.ruoyi.aps.domain.shared;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApsSharedValueObjectsTest
{
    @Test
    void idIsCanonicalUuid()
    {
        ApsId id = ApsId.newId();

        assertThat(id.value()).matches("[0-9a-f-]{36}");
        assertThatThrownBy(() -> new ApsId("NOT-A-UUID")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeWindowUsesHalfOpenSemantics()
    {
        Instant start = Instant.parse("2026-09-12T00:00:00Z");
        Instant end = Instant.parse("2026-09-12T01:00:00Z");
        UtcTimeWindow window = new UtcTimeWindow(start, end);

        assertThat(window.contains(start)).isTrue();
        assertThat(window.contains(end)).isFalse();
        assertThat(window.overlaps(new UtcTimeWindow(end, end.plusSeconds(60)))).isFalse();
    }

    @Test
    void quantityKeepsUnitAndRejectsNegativeValue()
    {
        assertThat(new ApsQuantity(new BigDecimal("1.2300"), "KG").value())
                .isEqualByComparingTo("1.23");
        assertThatThrownBy(() -> new ApsQuantity(new BigDecimal("-0.01"), "KG"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
