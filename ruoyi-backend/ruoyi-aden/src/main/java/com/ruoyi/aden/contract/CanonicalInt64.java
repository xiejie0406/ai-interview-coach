package com.ruoyi.aden.contract;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aden wire 协议中非负 64 位整数的边界值对象。
 *
 * <p>跨进程 JSON 必须传 canonical 十进制字符串；进入领域层后才转换为 {@code long}。
 * 该类型不接受 JSON number、符号、前导零、小数、指数或大于 {@link Long#MAX_VALUE} 的值。</p>
 */
public final class CanonicalInt64 implements Comparable<CanonicalInt64> {
    private static final Pattern CANONICAL_DECIMAL = Pattern.compile("(?:0|[1-9][0-9]{0,18})");

    private final long value;

    private CanonicalInt64(long value) {
        this.value = value;
    }

    /** 从通用 JSON 解码结果读取，确保调用方没有把 JSON number 隐式转成字符串。 */
    public static CanonicalInt64 parseWireValue(Object wireValue) {
        if (!(wireValue instanceof String decimal)) {
            throw new IllegalArgumentException("Aden 64-bit wire scalar 必须是 JSON string");
        }
        return parse(decimal);
    }

    /** 从 canonical 十进制字符串创建值对象。 */
    public static CanonicalInt64 parse(String decimal) {
        Objects.requireNonNull(decimal, "decimal");
        if (!CANONICAL_DECIMAL.matcher(decimal).matches()) {
            throw new IllegalArgumentException("Aden 64-bit wire scalar 不是 canonical 非负十进制字符串");
        }
        try {
            return new CanonicalInt64(Long.parseLong(decimal));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Aden 64-bit wire scalar 超出 Long.MAX_VALUE", exception);
        }
    }

    public long longValue() {
        return value;
    }

    /** 只能以字符串形式写回 wire，禁止先经过 JavaScript number 等有损中间值。 */
    public String toWireValue() {
        return Long.toString(value);
    }

    @Override
    public int compareTo(CanonicalInt64 other) {
        return Long.compare(value, Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CanonicalInt64 that && value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return toWireValue();
    }
}
