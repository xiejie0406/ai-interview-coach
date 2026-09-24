package com.ruoyi.aps.domain.shared;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * APS 业务主键。数据库统一保存为小写 RFC 4122 UUID 文本。
 */
public record ApsId(String value)
{
    public ApsId
    {
        Objects.requireNonNull(value, "value");
        String canonical = UUID.fromString(value).toString();
        if (!canonical.equals(value.toLowerCase(Locale.ROOT)))
        {
            throw new IllegalArgumentException("APS ID 必须是规范的小写 UUID");
        }
        value = canonical;
    }

    public static ApsId newId()
    {
        return new ApsId(UUID.randomUUID().toString());
    }

    @Override
    public String toString()
    {
        return value;
    }
}
