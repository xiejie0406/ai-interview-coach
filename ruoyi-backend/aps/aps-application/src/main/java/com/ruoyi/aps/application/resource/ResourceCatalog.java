package com.ruoyi.aps.application.resource;

import java.math.BigDecimal;
import java.time.Instant;
import com.ruoyi.aps.domain.resource.AvailabilityType;
import com.ruoyi.aps.domain.resource.ResourceStatus;
import com.ruoyi.aps.domain.resource.ResourceType;

/** M01～M05 应用层稳定数据形状，不暴露 MyBatis 或 HTTP 类型。 */
public final class ResourceCatalog
{
    private ResourceCatalog()
    {
    }

    public record Workshop(String id, String code, String name, String managerUserId, String status, String remark,
            long rowVersion)
    {
    }

    public record WorkCenter(String id, String workshopId, String code, String name, String centerType,
            BigDecimal concurrentCapacity, String capacityUomCode, String status, String remark, long rowVersion)
    {
    }

    public record Resource(String id, String workshopId, String workCenterId, String code, String name,
            ResourceType type, String ruoyiUserId, String teamName, BigDecimal capacityValue,
            String capacityUomCode, String status, String remark, long rowVersion)
    {
        public ResourceStatus resourceStatus()
        {
            return ResourceStatus.valueOf(status);
        }
    }

    public record Skill(String id, String resourceId, String code, String name, int level, Instant validFrom,
            Instant validTo, String certificateRef, String status, long rowVersion)
    {
    }

    public record Availability(String id, String resourceId, AvailabilityType type, Instant startAt, Instant endAt,
            BigDecimal capacityRatio, String sourceType, String sourceRef, String reason, long rowVersion)
    {
    }

    public record ReadinessIssue(String code, String objectType, String objectId, String message)
    {
    }

    public record Candidate(String resourceId, String resourceCode, String resourceName)
    {
    }
}
