package com.ruoyi.aps.solver.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 数量门槛、连续转移批和有限消耗预约的确定性十进制口径。 */
public final class QuantityReleaseMath
{
    private QuantityReleaseMath() { }

    public static BigDecimal gateQuantity(SolverInput.Dependency dependency, SolverInput.Task predecessor)
    {
        BigDecimal total = decimal(predecessor.quantity());
        BigDecimal gate = dependency.thresholdQty() != null
                ? decimal(dependency.thresholdQty())
                : total.multiply(decimal(dependency.thresholdRatio()));
        if ("PCS".equals(predecessor.uomCode())) gate = gate.setScale(0, RoundingMode.CEILING);
        return alignToTransferBatch(gate, dependency.transferBatchQty(), total);
    }

    /**
     * 消耗型后继按稳定 dependencyId 依次预约有限产出；返回每条数量依赖允许启动前必须实际释放的累计量。
     */
    public static Map<String, BigDecimal> requiredReleaseByDependency(List<SolverInput.Dependency> dependencies,
            Map<String, SolverInput.Task> tasks)
    {
        Map<String, BigDecimal> consumedByPredecessor = new LinkedHashMap<>();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (SolverInput.Dependency dependency : dependencies.stream()
                .filter(value -> value.relationType() == SolverInput.RelationType.QUANTITY)
                .sorted(Comparator.comparing(SolverInput.Dependency::predecessorTaskId)
                        .thenComparing(SolverInput.Dependency::dependencyId)).toList())
        {
            SolverInput.Task predecessor = requireTask(tasks, dependency.predecessorTaskId());
            BigDecimal required = gateQuantity(dependency, predecessor);
            if (dependency.consumesOutput())
            {
                SolverInput.Task successor = requireTask(tasks, dependency.successorTaskId());
                if (!predecessor.uomCode().equals(successor.uomCode()))
                    throw new IllegalArgumentException("消耗型数量依赖的前后任务单位必须一致");
                BigDecimal consumed = consumedByPredecessor.getOrDefault(predecessor.taskId(), BigDecimal.ZERO)
                        .add(decimal(successor.quantity()));
                if (consumed.compareTo(decimal(predecessor.quantity())) > 0)
                    throw new IllegalArgumentException("消耗型后继预约数量超过前置任务产出");
                consumedByPredecessor.put(predecessor.taskId(), consumed);
                required = required.max(alignToTransferBatch(consumed, dependency.transferBatchQty(),
                        decimal(predecessor.quantity())));
            }
            result.put(dependency.dependencyId(), required.stripTrailingZeros());
        }
        return Map.copyOf(result);
    }

    /** 生成候选中必须可追溯的连续释放里程碑，最后一个里程碑始终覆盖尾批。 */
    public static List<BigDecimal> releaseMilestones(SolverInput.Task predecessor,
            List<SolverInput.Dependency> outgoing, Map<String, BigDecimal> requiredByDependency)
    {
        BigDecimal total = decimal(predecessor.quantity());
        TreeSet<BigDecimal> values = new TreeSet<>();
        for (SolverInput.Dependency dependency : outgoing)
        {
            if (dependency.relationType() != SolverInput.RelationType.QUANTITY) continue;
            BigDecimal batch = nullableDecimal(dependency.transferBatchQty());
            if (batch != null)
            {
                for (BigDecimal value = batch; value.compareTo(total) < 0; value = value.add(batch))
                    values.add(value.stripTrailingZeros());
            }
            BigDecimal required = requiredByDependency.get(dependency.dependencyId());
            if (required != null && required.signum() > 0 && required.compareTo(total) < 0)
                values.add(required.stripTrailingZeros());
        }
        values.add(total.stripTrailingZeros());
        return List.copyOf(new ArrayList<>(values));
    }

    private static BigDecimal alignToTransferBatch(BigDecimal requested, String transferBatch, BigDecimal total)
    {
        BigDecimal batch = nullableDecimal(transferBatch);
        if (batch == null) return requested.min(total).stripTrailingZeros();
        BigDecimal batches = requested.divide(batch, 0, RoundingMode.CEILING);
        return batches.multiply(batch).min(total).stripTrailingZeros();
    }

    private static SolverInput.Task requireTask(Map<String, SolverInput.Task> tasks, String id)
    {
        SolverInput.Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("数量依赖引用未知任务: " + id);
        return task;
    }

    private static BigDecimal nullableDecimal(String value)
    {
        return value == null ? null : decimal(value);
    }

    private static BigDecimal decimal(String value)
    {
        BigDecimal result = new BigDecimal(value);
        if (result.signum() <= 0) throw new IllegalArgumentException("数量必须大于零");
        return result;
    }
}
