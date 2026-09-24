package com.ruoyi.aps.api.dto;

import java.time.Instant;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;

/** 严格拒绝带本地偏移的 APS 输入时间；持久化层再按 DATETIME(3) 保留毫秒。 */
public final class ApsUtcInput
{
    private ApsUtcInput()
    {
    }

    public static Instant parse(String value)
    {
        if (value == null || !value.matches(
                "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z$"))
        {
            throw invalid("时间必须使用 UTC Z 格式，允许 0 到 9 位小数秒");
        }
        try
        {
            return Instant.parse(value);
        }
        catch (RuntimeException exception)
        {
            throw invalid("时间值无效");
        }
    }

    private static ApsBusinessException invalid(String message)
    {
        return new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
    }
}
