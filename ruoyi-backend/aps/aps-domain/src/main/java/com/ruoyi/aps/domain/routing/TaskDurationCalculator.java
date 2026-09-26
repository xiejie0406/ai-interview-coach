package com.ruoyi.aps.domain.routing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 将阶段标准工时按冻结任务数量转换为整数秒。 */
public final class TaskDurationCalculator
{
    public int seconds(BigDecimal fixedSeconds, BigDecimal secondsPerUnit, BigDecimal taskQuantity)
    {
        if (fixedSeconds == null || secondsPerUnit == null || taskQuantity == null
                || fixedSeconds.signum() < 0 || secondsPerUnit.signum() < 0 || taskQuantity.signum() <= 0)
        {
            throw new IllegalArgumentException("工时和任务数量必须为非负有效值");
        }
        BigDecimal result = fixedSeconds.add(secondsPerUnit.multiply(taskQuantity)).setScale(0, RoundingMode.CEILING);
        return result.intValueExact();
    }
}
