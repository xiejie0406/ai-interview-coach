package com.ruoyi.aden.infrastructure.id;

import com.ruoyi.aden.domain.shared.AdenIdGenerator;

import java.util.UUID;

/** 使用标准小写 UUID 字符串生成 Aden 标识。 */
public final class UuidAdenIdGenerator implements AdenIdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
