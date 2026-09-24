package com.ruoyi.aps.domain.quantity;

import java.math.BigDecimal;
import java.util.Objects;

/** M27 一次报工的数量守恒值对象。 */
public record ProductionReportQuantities(
        BigDecimal processedQty,
        BigDecimal goodQty,
        BigDecimal pendingQty,
        BigDecimal rejectedQty,
        BigDecimal scrapQty,
        BigDecimal transferredQty)
{
    public ProductionReportQuantities
    {
        processedQty = nonNegative(processedQty, "processedQty");
        goodQty = nonNegative(goodQty, "goodQty");
        pendingQty = nonNegative(pendingQty, "pendingQty");
        rejectedQty = nonNegative(rejectedQty, "rejectedQty");
        scrapQty = nonNegative(scrapQty, "scrapQty");
        transferredQty = nonNegative(transferredQty, "transferredQty");
        if (processedQty.compareTo(goodQty.add(pendingQty).add(rejectedQty)) != 0)
        {
            throw new IllegalArgumentException("processedQty 必须等于 goodQty + pendingQty + rejectedQty");
        }
        if (scrapQty.compareTo(rejectedQty) > 0)
        {
            throw new IllegalArgumentException("scrapQty 不能超过 rejectedQty");
        }
        if (transferredQty.compareTo(goodQty) > 0)
        {
            throw new IllegalArgumentException("transferredQty 不能超过 goodQty");
        }
    }

    public void requirePositiveUnlessCorrection(boolean correction)
    {
        if (!correction && processedQty.signum() <= 0)
        {
            throw new IllegalArgumentException("普通报工的 processedQty 必须大于 0");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value, String name)
    {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0)
        {
            throw new IllegalArgumentException(name + " 不能为负数");
        }
        return value.stripTrailingZeros();
    }
}
