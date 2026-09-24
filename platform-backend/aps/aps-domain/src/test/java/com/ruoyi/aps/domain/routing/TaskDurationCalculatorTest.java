package com.ruoyi.aps.domain.routing;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDurationCalculatorTest
{
    @Test
    void usesCeilingForFractionalSeconds()
    {
        int seconds = new TaskDurationCalculator().seconds(new BigDecimal("10"), new BigDecimal("1.25"),
                new BigDecimal("3"));

        assertThat(seconds).isEqualTo(14);
    }
}
