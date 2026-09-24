package com.ruoyi.aps.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import org.junit.jupiter.api.Test;

class ApsUtcInputTest
{
    @Test
    void acceptsUtcAndRejectsLocalOffsetZeroLengthIsHandledByDomainWindow()
    {
        assertThat(ApsUtcInput.parse("2026-09-14T00:00:00.123Z"))
                .isEqualTo(Instant.parse("2026-09-14T00:00:00.123Z"));
        assertThatThrownBy(() -> ApsUtcInput.parse("2026-09-14T08:00:00.123+08:00"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(exception -> assertThat(((ApsBusinessException) exception).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_REQUEST));
    }
}
