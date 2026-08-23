package com.aiinterviewcoach.application.platform.port;

import com.aiinterviewcoach.domain.platform.IdempotencyKey;
import com.aiinterviewcoach.domain.platform.IdempotencyRecord;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Optional;

/** 原子 claim 由持久化实现保证；同 key 异 payload 必须返回冲突而非覆盖。 */
public interface IdempotencyPort {

    Optional<IdempotencyRecord> find(
            TenantId tenantId,
            String principalRefHash,
            String operation,
            IdempotencyKey key
    );

    boolean claim(IdempotencyRecord record);

    void save(IdempotencyRecord record);
}
