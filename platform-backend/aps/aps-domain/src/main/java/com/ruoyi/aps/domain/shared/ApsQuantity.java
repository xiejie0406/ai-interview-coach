package com.ruoyi.aps.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 带单位的非负数量。跨单位运算必须由显式换算规则完成。
 */
public record ApsQuantity(BigDecimal value, String uomCode)
{
    private static final Pattern UOM_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,15}$");

    public ApsQuantity
    {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(uomCode, "uomCode");
        if (value.signum() < 0)
        {
            throw new IllegalArgumentException("数量不能为负数");
        }
        if (!UOM_PATTERN.matcher(uomCode).matches())
        {
            throw new IllegalArgumentException("单位编码必须是 1 至 16 位大写标识符");
        }
        value = value.stripTrailingZeros();
    }
}
