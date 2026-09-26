package com.ruoyi.aps.application.foundation;

import java.util.List;
import java.util.Objects;

/**
 * 稳定分页结果，不向应用层暴露 PageHelper 类型。
 */
public record ApsPage<T>(List<T> items, long total, int pageNumber, int pageSize)
{
    public ApsPage
    {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (total < 0 || pageNumber < 1 || pageSize < 1)
        {
            throw new IllegalArgumentException("分页元数据无效");
        }
    }
}
