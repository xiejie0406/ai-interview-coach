package com.ruoyi.aps.application.foundation;

/**
 * 与 PageHelper、HTTP 参数无关的应用层分页请求。
 */
public record ApsPageRequest(int pageNumber, int pageSize)
{
    public static final int MAX_PAGE_SIZE = 200;

    public ApsPageRequest
    {
        if (pageNumber < 1)
        {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException("每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
    }

    public long offset()
    {
        return (long) (pageNumber - 1) * pageSize;
    }
}
