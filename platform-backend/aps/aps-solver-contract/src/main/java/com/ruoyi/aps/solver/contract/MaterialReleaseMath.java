package com.ruoyi.aps.solver.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 固定供给和任务产出的有限预约、质量放行与就绪时点口径。 */
public final class MaterialReleaseMath
{
    private MaterialReleaseMath() { }

    public static ReleasePlan plan(SolverInput input)
    {
        Map<String, SolverInput.Task> tasks = input.tasks().stream().collect(
                java.util.stream.Collectors.toMap(SolverInput.Task::taskId, value -> value));
        Map<String, BigDecimal> internalConsumed = new HashMap<>();
        Map<String, BigDecimal> internalRequired = new LinkedHashMap<>();
        Map<String, Instant> internalReady = new LinkedHashMap<>();
        Map<String, Instant> externalReady = new LinkedHashMap<>();
        Map<String, BigDecimal> externalConsumed = new HashMap<>();
        Map<String, List<SolverInput.MaterialSupply>> fixed = input.materialSupplies().stream()
                .filter(value -> value.sourceTaskId() == null && "AVAILABLE".equals(value.qualityState()))
                .collect(java.util.stream.Collectors.groupingBy(MaterialReleaseMath::key));
        Map<String, List<SolverInput.MaterialSupply>> executionReleased = input.materialSupplies().stream()
                .filter(value -> value.sourceTaskId() != null && "EXECUTION_RELEASED".equals(value.supplyType())
                        && "AVAILABLE".equals(value.qualityState()))
                .collect(java.util.stream.Collectors.groupingBy(SolverInput.MaterialSupply::sourceTaskId));

        for (SolverInput.MaterialDemand demand : input.materialDemands().stream()
                .filter(value -> value.materialStatus() != SolverInput.MaterialStatus.CANCELLED)
                .sorted(Comparator.comparing(SolverInput.MaterialDemand::demandId)).toList())
        {
            if (demand.sourceTaskId() != null)
            {
                SolverInput.Task source = requireTask(tasks, demand.sourceTaskId());
                requireTask(tasks, demand.targetTaskId());
                if (!source.uomCode().equals(demand.uomCode()))
                    throw new IllegalArgumentException("任务产出与物料需求单位不一致");
                BigDecimal consumed = internalConsumed.getOrDefault(source.taskId(), BigDecimal.ZERO)
                        .add(decimal(demand.requiredQuantity()));
                BigDecimal actual = executionReleased.getOrDefault(source.taskId(), List.of()).stream()
                        .map(value -> decimal(value.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal total = decimal(source.quantity()).add(actual);
                if (consumed.compareTo(total) > 0) throw new IllegalArgumentException("物料需求预约超过来源任务实际放行与剩余有限产出");
                internalConsumed.put(source.taskId(), consumed);
                if (consumed.compareTo(actual) <= 0)
                {
                    BigDecimal released = BigDecimal.ZERO;
                    for (SolverInput.MaterialSupply supply : executionReleased.getOrDefault(source.taskId(), List.of())
                            .stream().sorted(Comparator.comparing(SolverInput.MaterialSupply::availableAt)
                                    .thenComparing(SolverInput.MaterialSupply::supplyId)).toList())
                    {
                        released = released.add(decimal(supply.quantity()));
                        if (released.compareTo(consumed) >= 0)
                        {
                            internalReady.put(demand.demandId(), supply.availableAt());
                            break;
                        }
                    }
                    internalRequired.put(demand.demandId(), BigDecimal.ZERO);
                }
                else internalRequired.put(demand.demandId(), align(consumed.subtract(actual),
                        demand.transferBatchQuantity(), decimal(source.quantity())));
                continue;
            }
            if (demand.materialStatus() == SolverInput.MaterialStatus.CONSUMED)
            {
                if (demand.readyAt() == null) throw new IllegalArgumentException("已消耗物料必须提供实际就绪时间");
                externalReady.put(demand.demandId(), demand.readyAt());
                continue;
            }
            if (demand.materialStatus() != SolverInput.MaterialStatus.AVAILABLE)
                throw new IllegalArgumentException("外部物料尚未完整质量放行");
            String key = key(demand);
            BigDecimal needed = externalConsumed.getOrDefault(key, BigDecimal.ZERO)
                    .add(decimal(demand.requiredQuantity()));
            externalConsumed.put(key, needed);
            BigDecimal released = BigDecimal.ZERO;
            Instant ready = null;
            for (SolverInput.MaterialSupply supply : fixed.getOrDefault(key, List.of()).stream()
                    .sorted(Comparator.comparing(SolverInput.MaterialSupply::availableAt)
                            .thenComparing(SolverInput.MaterialSupply::supplyId)).toList())
            {
                released = released.add(decimal(supply.quantity()));
                if (released.compareTo(needed) >= 0) { ready = supply.availableAt(); break; }
            }
            if (ready == null) throw new IllegalArgumentException("已质量放行的外部物料有限供给不足");
            if (demand.readyAt() != null && demand.readyAt().isAfter(ready)) ready = demand.readyAt();
            externalReady.put(demand.demandId(), ready);
        }
        return new ReleasePlan(Map.copyOf(internalRequired), Map.copyOf(internalReady), Map.copyOf(externalReady));
    }

    public static List<BigDecimal> releaseMilestones(SolverInput.Task source,
            List<SolverInput.MaterialDemand> outgoing, ReleasePlan plan)
    {
        BigDecimal total = decimal(source.quantity());
        TreeSet<BigDecimal> values = new TreeSet<>();
        for (SolverInput.MaterialDemand demand : outgoing)
        {
            BigDecimal batch = nullable(demand.transferBatchQuantity());
            if (batch != null)
                for (BigDecimal value = batch; value.compareTo(total) < 0; value = value.add(batch)) values.add(value);
            BigDecimal required = plan.internalRequiredByDemand().get(demand.demandId());
            if (required != null && required.compareTo(total) < 0) values.add(required);
        }
        values.add(total);
        return List.copyOf(new ArrayList<>(values));
    }

    private static BigDecimal align(BigDecimal value, String batchText, BigDecimal total)
    {
        BigDecimal batch = nullable(batchText);
        if (batch == null) return value.stripTrailingZeros();
        return value.divide(batch, 0, RoundingMode.CEILING).multiply(batch).min(total).stripTrailingZeros();
    }

    private static String key(SolverInput.MaterialSupply value) { return value.itemId() + '\u0000' + value.uomCode(); }
    private static String key(SolverInput.MaterialDemand value) { return value.itemId() + '\u0000' + value.uomCode(); }
    private static SolverInput.Task requireTask(Map<String, SolverInput.Task> tasks, String id)
    {
        SolverInput.Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("物料关系引用未知任务: " + id);
        return task;
    }
    private static BigDecimal nullable(String value) { return value == null ? null : decimal(value); }
    private static BigDecimal decimal(String value)
    {
        BigDecimal result = new BigDecimal(value);
        if (result.signum() <= 0) throw new IllegalArgumentException("物料数量必须大于零");
        return result.stripTrailingZeros();
    }

    public record ReleasePlan(Map<String, BigDecimal> internalRequiredByDemand,
            Map<String, Instant> internalReadyAtByDemand,
            Map<String, Instant> externalReadyAtByDemand)
    {
        public ReleasePlan
        {
            internalRequiredByDemand = Map.copyOf(internalRequiredByDemand);
            internalReadyAtByDemand = Map.copyOf(internalReadyAtByDemand);
            externalReadyAtByDemand = Map.copyOf(externalReadyAtByDemand);
        }
    }
}
