package com.ruoyi.aden.domain.task;

public record AdenTaskVersion(long value) {
    public AdenTaskVersion {
        if (value < 0) throw new IllegalArgumentException("task version 不得为负数");
    }

    public AdenTaskVersion next() {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("task version 已耗尽");
        return new AdenTaskVersion(value + 1);
    }
}
