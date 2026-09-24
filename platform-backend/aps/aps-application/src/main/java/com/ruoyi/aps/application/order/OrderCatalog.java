package com.ruoyi.aps.application.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.application.routing.RoutingCatalog.ResourceRequirement;
import com.ruoyi.aps.domain.routing.DependencyType;

/** M13～M18 的订单、批次和已冻结任务网络。 */
public final class OrderCatalog
{
    private OrderCatalog() { }

    public enum LotType { NORMAL, SPLIT, REWORK, REPLENISH }
    public enum DemandType { COMPONENT, TRANSFER, EXTERNAL }

    public record ComponentSpec(int demandNo, String targetNodeCode, String itemId, Integer sourceLineNo,
            String sourceNodeCode, DemandType demandType, BigDecimal requiredQtyPerUnit, String uomCode,
            BigDecimal transferBatchQty) { }

    /** 显式跨产品依赖；存入目标订单行快照，绝不由页面或行号顺序推断。 */
    public record LineDependencySpec(int predecessorLineNo, String predecessorNodeCode, String successorNodeCode,
            DependencyType dependencyType, BigDecimal thresholdQty, BigDecimal thresholdRatio,
            BigDecimal transferBatchQty, int lagSeconds, boolean consumesOutput) { }

    public record OrderLine(String id, String orderId, int lineNo, String itemId, String routeVersionId,
            BigDecimal demandQty, String uomCode, Instant promisedAt, Instant earliestStartAt, String status,
            List<ComponentSpec> components, List<LineDependencySpec> dependencies, long rowVersion)
    {
        public OrderLine
        {
            components = components == null ? List.of() : List.copyOf(components);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    public record ProductionOrder(String id, String orderNo, String sourceSystem, String externalId,
            String customerCode, String customerName, int priority, Instant promisedAt, Instant earliestStartAt,
            String status, String remark, List<OrderLine> lines, long rowVersion)
    {
        public ProductionOrder
        {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record ProductionLot(String id, String orderLineId, String itemId, String routeVersionId,
            String parentLotId, String lotNo, LotType lotType, BigDecimal plannedQty, String uomCode,
            String recoveryReason, String status, long rowVersion) { }

    public record Task(String id, String productionLotId, String routeVersionId, String routeNodeId,
            String operationSpecId, String workCenterId, String taskCode, String taskName, BigDecimal taskQty,
            String uomCode, int setupSeconds, int runSeconds, int unloadSeconds, int waitSeconds,
            int transportSeconds, boolean interruptible, String requiredSkillCode, Integer minimumSkillLevel,
            List<ResourceRequirement> requirementSnapshot, String status, long rowVersion)
    {
        public Task
        {
            requirementSnapshot = requirementSnapshot == null ? List.of() : List.copyOf(requirementSnapshot);
        }
    }

    public record TaskDependency(String id, String predecessorTaskId, String successorTaskId,
            DependencyType dependencyType, BigDecimal thresholdQty, BigDecimal thresholdRatio,
            BigDecimal transferBatchQty, String uomCode, int lagSeconds, boolean consumesOutput,
            long rowVersion) { }

    public record MaterialDemand(String id, String targetTaskId, int demandNo, String itemId,
            String sourceTaskId, DemandType demandType, BigDecimal requiredQty, String uomCode,
            BigDecimal transferBatchQty, String materialStatus, Instant readyAt, long rowVersion) { }

    public record ExpansionResult(ProductionOrder order, List<ProductionLot> lots, List<Task> tasks,
            List<TaskDependency> dependencies, List<MaterialDemand> materialDemands, boolean reused)
    {
        public ExpansionResult
        {
            lots = List.copyOf(lots);
            tasks = List.copyOf(tasks);
            dependencies = List.copyOf(dependencies);
            materialDemands = List.copyOf(materialDemands);
        }
    }

    public record MigrationDiff(String orderLineId, String currentRouteVersionId, String targetRouteVersionId,
            List<String> addedNodes, List<String> removedNodes, List<String> changedOperations,
            List<String> addedEdges, List<String> removedEdges, boolean confirmationRequired) { }
}
