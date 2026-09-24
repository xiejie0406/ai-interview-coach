package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeAxisTest
{
    @Test
    void compilesUtcTimeToNonNegativeIntegerUnits()
    {
        TimeAxis axis = new TimeAxis(Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-16T00:00:00Z"), 60);

        assertThat(axis.floorOffset(Instant.parse("2026-09-15T01:01:59Z"))).isEqualTo(61);
        assertThat(axis.ceilDurationSeconds(61)).isEqualTo(2);
        assertThatThrownBy(() -> axis.floorOffset(Instant.parse("2026-09-14T23:59:59Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsOffsetsInBothDirectionsWithoutSchedulingBeforeTheSourceInstant()
    {
        TimeAxis axis = new TimeAxis(Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-15T01:00:00Z"), 60);

        assertThat(axis.floorOffset(Instant.parse("2026-09-15T00:01:59Z"))).isEqualTo(1);
        assertThat(axis.ceilOffset(Instant.parse("2026-09-15T00:01:00.001Z"))).isEqualTo(2);
        assertThat(axis.horizonUnits()).isEqualTo(60);
        assertThat(axis.instantAt(2)).isEqualTo(Instant.parse("2026-09-15T00:02:00Z"));
    }
}
