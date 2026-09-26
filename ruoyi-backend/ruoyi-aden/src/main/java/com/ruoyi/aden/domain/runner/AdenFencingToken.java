package com.ruoyi.aden.domain.runner;

public record AdenFencingToken(long value) {
    public AdenFencingToken {
        if (value < 0) throw new IllegalArgumentException("fencing token 不得为负数");
    }

    public AdenFencingToken next() {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("fencing token 已耗尽");
        return new AdenFencingToken(value + 1);
    }
}
