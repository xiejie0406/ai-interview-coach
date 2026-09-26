package com.ruoyi.aden.infrastructure.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 在 MySQL DATETIME(6) 与领域 Instant 之间执行显式 UTC 转换。 */
public final class AdenUtcDateTimeCodec {
    private AdenUtcDateTimeCodec() {
    }

    public static LocalDateTime toDatabase(Instant instant) {
        return LocalDateTime.ofInstant(Objects.requireNonNull(instant, "instant"), ZoneOffset.UTC);
    }

    public static Instant fromDatabase(LocalDateTime value) {
        return Objects.requireNonNull(value, "value").toInstant(ZoneOffset.UTC);
    }
}
