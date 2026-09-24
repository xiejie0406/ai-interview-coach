package com.ruoyi.fashion.domain.shared;

/** 内部 BIGINT ID 值对象；API 边界必须以十进制字符串传递。 */
public record FashionId(long value) {
    public FashionId {
        if (value <= 0) {
            throw new IllegalArgumentException("Fashion ID 必须为正整数");
        }
    }

    public static FashionId parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Fashion ID 必须为十进制正整数字符串");
        }
        try {
            return new FashionId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Fashion ID 超出 BIGINT 正整数范围", exception);
        }
    }

    public String externalValue() {
        return Long.toString(value);
    }
}
