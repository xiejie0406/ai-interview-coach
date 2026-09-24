package com.ruoyi.aps.application.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OutputLot;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QualityDecision;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.order.OrderCatalog.LotType;
import com.ruoyi.aps.application.order.OrderCatalog.MaterialDemand;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionLot;
import com.ruoyi.aps.application.order.OrderCatalog.Task;
import com.ruoyi.aps.application.order.OrderCatalog.TaskDependency;
import com.ruoyi.aps.application.order.OrderRepository;

/** 质量返工/报废补产创建 M15～M18 派生网络；调用方必须置于质量事务内。 */
public final class RecoveryOrderService
{
    private final OrderRepository orders;

    public RecoveryOrderService(OrderRepository orders)
    {
        this.orders = Objects.requireNonNull(orders, "orders");
    }

    public ProductionLot create(OutputLot outputLot, QualityDecision decision, BigDecimal quantity,
            String reason, String requestId, String actor)
    {
        LotType type = switch (decision)
        {
            case REWORK -> LotType.REWORK;
            case SCRAP -> LotType.REPLENISH;
            default -> throw new IllegalArgumentException("只有返工或报废补产可创建派生生产批");
        };
        ProductionLot parent = orders.findLot(outputLot.productionLotId())
                .orElseThrow(() -> error("来源生产批不存在"));
        String lotId = stable("recovery-lot", requestId, type.name());
        var existing = orders.findLot(lotId);
        if (existing.isPresent())
        {
            ProductionLot value = existing.get();
            if (value.lotType() != type || !Objects.equals(value.parentLotId(), parent.id())
                    || value.plannedQty().compareTo(quantity) != 0 || !Objects.equals(value.recoveryReason(), reason))
                throw new ApsBusinessException(ApsErrorCode.IDEMPOTENCY_CONFLICT,
                        "同一幂等键已绑定其他返工或补产意图");
            return value;
        }
        ProductionLot recovery = new ProductionLot(lotId, parent.orderLineId(), parent.itemId(),
                parent.routeVersionId(), parent.id(), recoveryLotNo(parent.lotNo(), type, lotId), type,
                quantity.stripTrailingZeros(), parent.uomCode(), reason, "RELEASED", 0);
        orders.insertLot(recovery, actor);
        cloneNetwork(parent, recovery, actor);
        return recovery;
    }

    private void cloneNetwork(ProductionLot parent, ProductionLot recovery, String actor)
    {
        List<Task> sourceTasks = orders.listTasksByLot(parent.id());
        if (sourceTasks.isEmpty()) throw error("来源生产批没有可复制的冻结任务网络");
        Map<String, Task> cloned = new HashMap<>();
        for (Task source : sourceTasks)
        {
            BigDecimal taskQty = source.taskQty().multiply(recovery.plannedQty())
                    .divide(parent.plannedQty(), 6, RoundingMode.HALF_UP).stripTrailingZeros();
            Task target = new Task(stable(recovery.id(), "task", source.id()), recovery.id(),
                    source.routeVersionId(), source.routeNodeId(), source.operationSpecId(), source.workCenterId(),
                    boundedCode(source.taskCode(), recovery.lotType(), recovery.id()), source.taskName(), taskQty,
                    source.uomCode(), source.setupSeconds(), scaled(source.runSeconds(), taskQty, source.taskQty()),
                    source.unloadSeconds(), source.waitSeconds(), source.transportSeconds(), source.interruptible(),
                    source.requiredSkillCode(), source.minimumSkillLevel(), source.requirementSnapshot(),
                    "NOT_READY", 0);
            orders.insertTask(target, actor);
            cloned.put(source.id(), target);
        }

        String orderId = orders.findOrderLine(parent.orderLineId()).orElseThrow().orderId();
        for (TaskDependency source : orders.listDependencies(orderId))
        {
            Task predecessor = cloned.get(source.predecessorTaskId());
            Task successor = cloned.get(source.successorTaskId());
            if (predecessor == null || successor == null) continue;
            BigDecimal threshold = source.thresholdQty() == null ? null
                    : source.thresholdQty().multiply(recovery.plannedQty())
                            .divide(parent.plannedQty(), 6, RoundingMode.HALF_UP).min(predecessor.taskQty())
                            .stripTrailingZeros();
            BigDecimal transfer = source.transferBatchQty() == null ? null
                    : source.transferBatchQty().min(predecessor.taskQty()).stripTrailingZeros();
            orders.insertDependency(new TaskDependency(stable(recovery.id(), "dependency", source.id()),
                    predecessor.id(), successor.id(), source.dependencyType(), threshold, source.thresholdRatio(),
                    transfer, source.uomCode(), source.lagSeconds(), source.consumesOutput(), 0), actor);
        }

        Map<String, List<MaterialDemand>> demandsByTask = orders.listMaterialDemands(orderId).stream()
                .collect(java.util.stream.Collectors.groupingBy(MaterialDemand::targetTaskId));
        for (Task source : sourceTasks)
        {
            for (MaterialDemand demand : demandsByTask.getOrDefault(source.id(), List.of()))
            {
                Task target = cloned.get(source.id());
                String sourceTaskId = cloned.containsKey(demand.sourceTaskId())
                        ? cloned.get(demand.sourceTaskId()).id() : demand.sourceTaskId();
                BigDecimal required = demand.requiredQty().multiply(recovery.plannedQty())
                        .divide(parent.plannedQty(), 6, RoundingMode.HALF_UP).stripTrailingZeros();
                BigDecimal transfer = demand.transferBatchQty() == null ? null
                        : demand.transferBatchQty().min(required).stripTrailingZeros();
                orders.insertMaterialDemand(new MaterialDemand(stable(target.id(), "demand",
                        Integer.toString(demand.demandNo())), target.id(), demand.demandNo(), demand.itemId(),
                        sourceTaskId, demand.demandType(), required, demand.uomCode(), transfer,
                        "UNAVAILABLE", null, 0), actor);
            }
        }
    }

    private int scaled(int seconds, BigDecimal targetQty, BigDecimal sourceQty)
    {
        if (seconds == 0) return 0;
        return BigDecimal.valueOf(seconds).multiply(targetQty).divide(sourceQty, 0, RoundingMode.CEILING)
                .intValueExact();
    }

    private String recoveryLotNo(String parent, LotType type, String id)
    {
        String suffix = "-" + (type == LotType.REWORK ? "RW" : "RP") + "-" + id.replace("-", "").substring(0, 12);
        return parent.substring(0, Math.min(parent.length(), 64 - suffix.length())) + suffix;
    }

    private String boundedCode(String source, LotType type, String id)
    {
        String suffix = "-" + (type == LotType.REWORK ? "RW" : "RP") + "-" + id.replace("-", "").substring(0, 8);
        return source.substring(0, Math.min(source.length(), 64 - suffix.length())) + suffix;
    }

    private static String stable(String... parts)
    {
        return UUID.nameUUIDFromBytes(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ApsBusinessException error(String message)
    {
        return new ApsBusinessException(ApsErrorCode.CONFLICT, message);
    }
}
