package com.ruoyi.fashion.infrastructure.persistence.foundation;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.UUID;

import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import org.springframework.stereotype.Component;

/** UUID 随机位映射为正 BIGINT；0 被跳过，碰撞由数据库主键最终兜底。 */
@FashionModuleEnabled
@Component
public final class UuidFashionIdGenerator implements FashionIdGenerator {
    @Override
    public long nextId() {
        long value;
        do {
            value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        } while (value == 0L);
        return value;
    }
}
