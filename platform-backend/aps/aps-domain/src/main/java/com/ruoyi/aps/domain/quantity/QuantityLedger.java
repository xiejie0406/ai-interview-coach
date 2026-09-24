package com.ruoyi.aps.domain.quantity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 由 M29 追加事件复算 M28 当前数量投影的纯领域模型。 */
public final class QuantityLedger
{
    public enum Bucket
    {
        PENDING_QUALITY, AVAILABLE, RESERVED, CONSUMED, TRANSFERRED, SCRAPPED, ADJUSTMENT
    }

    public enum EventType
    {
        PRODUCE, QUALITY_RELEASE, RESERVE, UNRESERVE, CONSUME, TRANSFER, SCRAP, ADJUST, REVERSE
    }

    public record Movement(EventType eventType, Bucket fromBucket, Bucket toBucket, BigDecimal quantity)
    {
        public Movement
        {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(quantity, "quantity");
            quantity = quantity.stripTrailingZeros();
            if (quantity.signum() <= 0)
            {
                throw new IllegalArgumentException("数量事件必须大于 0");
            }
            if (fromBucket != null && fromBucket == toBucket)
            {
                throw new IllegalArgumentException("数量事件起止桶不能相同");
            }
            validateShape(eventType, fromBucket, toBucket);
        }

        public static Movement produce(BigDecimal quantity)
        {
            return new Movement(EventType.PRODUCE, null, Bucket.PENDING_QUALITY, quantity);
        }

        public static Movement reverse(Movement original)
        {
            Objects.requireNonNull(original, "original");
            if (original.eventType() == EventType.REVERSE)
            {
                throw new IllegalArgumentException("不能再次反向 REVERSE 事件");
            }
            return new Movement(EventType.REVERSE, original.toBucket(), original.fromBucket(), original.quantity());
        }

        public void requireExactReversalOf(Movement original)
        {
            Objects.requireNonNull(original, "original");
            if (eventType != EventType.REVERSE || fromBucket != original.toBucket()
                    || toBucket != original.fromBucket() || quantity.compareTo(original.quantity()) != 0)
            {
                throw new IllegalArgumentException("REVERSE 必须精确交换原事件起止桶并保持数量一致");
            }
        }

        private static void validateShape(EventType type, Bucket from, Bucket to)
        {
            boolean valid = switch (type)
            {
                case PRODUCE -> from == null && to == Bucket.PENDING_QUALITY;
                case QUALITY_RELEASE -> from == Bucket.PENDING_QUALITY && to == Bucket.AVAILABLE;
                case RESERVE -> from == Bucket.AVAILABLE && to == Bucket.RESERVED;
                case UNRESERVE -> from == Bucket.RESERVED && to == Bucket.AVAILABLE;
                case CONSUME -> (from == Bucket.AVAILABLE || from == Bucket.RESERVED) && to == Bucket.CONSUMED;
                case TRANSFER -> from == Bucket.AVAILABLE && to == Bucket.TRANSFERRED;
                case SCRAP -> (from == Bucket.PENDING_QUALITY || from == Bucket.AVAILABLE
                        || from == Bucket.RESERVED) && to == Bucket.SCRAPPED;
                case ADJUST -> from != null && to != null;
                case REVERSE -> from != null || to != null;
            };
            if (!valid)
            {
                throw new IllegalArgumentException("数量事件桶形状与事件类型不匹配：" + type);
            }
        }
    }

    private final EnumMap<Bucket, BigDecimal> balances = new EnumMap<>(Bucket.class);

    public QuantityLedger()
    {
        for (Bucket bucket : Bucket.values())
        {
            balances.put(bucket, BigDecimal.ZERO);
        }
    }

    public QuantityLedger apply(List<Movement> movements)
    {
        Objects.requireNonNull(movements, "movements");
        movements.forEach(this::apply);
        return this;
    }

    public QuantityLedger apply(Movement movement)
    {
        Objects.requireNonNull(movement, "movement");
        if (movement.fromBucket() != null)
        {
            BigDecimal remaining = balance(movement.fromBucket()).subtract(movement.quantity());
            if (remaining.signum() < 0)
            {
                throw new IllegalStateException("数量桶不能为负数：" + movement.fromBucket());
            }
            balances.put(movement.fromBucket(), remaining);
        }
        if (movement.toBucket() != null)
        {
            balances.put(movement.toBucket(), balance(movement.toBucket()).add(movement.quantity()));
        }
        return this;
    }

    public BigDecimal balance(Bucket bucket)
    {
        return balances.get(Objects.requireNonNull(bucket, "bucket"));
    }

    public Map<Bucket, BigDecimal> snapshot()
    {
        return Collections.unmodifiableMap(new EnumMap<>(balances));
    }
}
