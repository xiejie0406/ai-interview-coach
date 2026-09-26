package com.ruoyi.aps.application.resource;

/** APS 资源数据范围。P0 中管理员/范围管理员可访问全部，其余用户仅访问其负责车间。 */
public record ResourceAccessScope(String actorUserId, boolean allWorkshops)
{
    public ResourceAccessScope
    {
        if (actorUserId == null || actorUserId.isBlank())
        {
            throw new IllegalArgumentException("数据范围操作者不能为空");
        }
    }
}
