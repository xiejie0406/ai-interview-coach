package com.aiinterviewcoach.adapters.outbound.platform;

import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.UUID;
import java.util.function.Supplier;

/** UUID v4 候选生成器；格式仍封装在强类型 ID 中，不作为授权或排序依据。 */
public final class UuidIdGeneratorAdapter implements IdGeneratorPort {

    private final Supplier<UUID> uuidSource;

    public UuidIdGeneratorAdapter(Supplier<UUID> uuidSource) {
        this.uuidSource = java.util.Objects.requireNonNull(uuidSource);
    }

    @Override
    public ResourceId nextResourceId() {
        return ResourceId.of(next());
    }

    @Override
    public TenantId nextTenantId() {
        return TenantId.of(next());
    }

    @Override
    public UserId nextUserId() {
        return UserId.of(next());
    }

    private UUID next() {
        return java.util.Objects.requireNonNull(uuidSource.get(), "generated UUID");
    }
}
