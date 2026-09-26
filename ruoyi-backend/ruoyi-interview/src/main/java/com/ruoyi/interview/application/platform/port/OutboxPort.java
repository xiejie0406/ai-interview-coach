package com.ruoyi.interview.application.platform.port;

import com.ruoyi.interview.domain.platform.OutboxEvent;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxPort {

    Optional<OutboxEvent> find(TenantId tenantId, ResourceId eventId);

    void save(OutboxEvent event);

    /** 仅供批准的系统 Publisher 扫描；后续 claim/publish/fail 必须按事件 tenantId 重新作用域。 */
    List<OutboxEvent> findPublishable(Instant availableBefore, int limit);

    /** 锁定过期 CLAIMED delivery；调用方必须在同一事务内 reclaim 并保存。 */
    List<OutboxEvent> findExpiredClaims(Instant expiredBefore, int limit);
}
