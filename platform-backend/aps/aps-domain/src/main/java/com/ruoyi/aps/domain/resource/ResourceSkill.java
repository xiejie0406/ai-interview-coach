package com.ruoyi.aps.domain.resource;

import java.time.Instant;

/** 一行代表资源的一项技能；同一资源可拥有多行不同 skillCode。 */
public record ResourceSkill(String id, String resourceId, String skillCode, String skillName, int skillLevel,
        Instant validFrom, Instant validTo, String certificateRef, String status)
{
    public ResourceSkill
    {
        if (id == null || id.isBlank() || resourceId == null || resourceId.isBlank()
                || skillCode == null || skillCode.isBlank() || skillName == null || skillName.isBlank())
        {
            throw new IllegalArgumentException("技能标识、资源、编码和名称不能为空");
        }
        if (skillLevel < 1 || skillLevel > 10)
        {
            throw new IllegalArgumentException("技能等级必须在 1 到 10 之间");
        }
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom))
        {
            throw new IllegalArgumentException("技能有效期结束必须晚于开始");
        }
    }

    public boolean qualifies(String requiredCode, int minimumLevel, Instant at)
    {
        return "ACTIVE".equals(status) && skillCode.equals(requiredCode) && skillLevel >= minimumLevel
                && (validFrom == null || !at.isBefore(validFrom)) && (validTo == null || at.isBefore(validTo));
    }
}
