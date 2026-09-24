package com.ruoyi.aps.infrastructure.mysql.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public abstract class ApsRowMapperSupport
{
    protected String text(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
    protected String nullable(Map<String, Object> row, String key) { return row.get(key) == null ? null : String.valueOf(row.get(key)); }
    protected Number number(Map<String, Object> row, String key) { return (Number) row.get(key); }
    protected BigDecimal decimal(Map<String, Object> row, String key)
    {
        return row.get(key) instanceof BigDecimal value ? value : new BigDecimal(String.valueOf(row.get(key)));
    }
    protected BigDecimal nullableDecimal(Map<String, Object> row, String key)
    {
        return row.get(key) == null ? null : decimal(row, key);
    }
    protected boolean bool(Map<String, Object> row, String key)
    {
        Object value = row.get(key);
        return value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
    }
    protected Instant instant(Object value)
    {
        if (value == null) return null;
        if (value instanceof Instant result) return result;
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalArgumentException("不支持的数据库时间类型: " + value.getClass().getName());
    }
}
