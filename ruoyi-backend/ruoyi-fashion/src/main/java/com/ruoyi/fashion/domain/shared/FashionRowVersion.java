package com.ruoyi.fashion.domain.shared;

/** 乐观锁版本从 1 开始；更新语句必须以旧值作条件并原子递增。 */
public record FashionRowVersion(long value) {
    public FashionRowVersion {
        if (value < 1) {
            throw new IllegalArgumentException("row_version 必须大于等于 1");
        }
    }

    public FashionRowVersion next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("row_version 已达到上限");
        }
        return new FashionRowVersion(value + 1);
    }
}
