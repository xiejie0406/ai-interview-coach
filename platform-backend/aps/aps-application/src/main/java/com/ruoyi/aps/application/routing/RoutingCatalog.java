package com.ruoyi.aps.application.routing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;
import com.ruoyi.aps.domain.routing.DependencyType;
import com.ruoyi.aps.domain.resource.ResourceType;

/** M06～M12 的应用层数据形状。 */
public final class RoutingCatalog
{
    private RoutingCatalog() { }

    public enum ItemType { PRODUCT, SEMI_FINISHED, MATERIAL }
    public enum OperationMode { MANUAL, MAN_MACHINE, AUTO, BATCH, WAIT, TRANSPORT }
    public enum PhaseType { SETUP, RUN, UNLOAD, WAIT, TRANSPORT }
    public enum DurationModel { FIXED, PER_UNIT, FIXED_PLUS_UNIT }
    public enum ResourceHoldPolicy { PHASE_ONLY, UNTIL_NEXT_PHASE, WHOLE_JOB }
    public enum SegmentResourcePolicy { SAME_RESOURCES, RESELECT_ALLOWED }

    public record Item(String id, String code, String name, ItemType type, String specification,
            String baseUomCode, String status, String remark, long rowVersion) { }

    public record ResourceRequirement(String id, String phaseId, int requirementNo, ResourceType resourceType,
            String workCenterId, String fixedResourceId, int seatCount, String requiredSkillCode,
            Integer minimumSkillLevel, String capabilityRuleJson, boolean optional, boolean holdOnPause,
            long rowVersion)
    {
        public ResourceRequirement(String id, String phaseId, int requirementNo, ResourceType resourceType,
                String workCenterId, String fixedResourceId, int seatCount, String requiredSkillCode,
                Integer minimumSkillLevel, String capabilityRuleJson, boolean optional, long rowVersion)
        {
            this(id, phaseId, requirementNo, resourceType, workCenterId, fixedResourceId, seatCount,
                    requiredSkillCode, minimumSkillLevel, capabilityRuleJson, optional, false, rowVersion);
        }
    }

    public record OperationPhase(String id, String operationSpecId, int phaseNo, PhaseType phaseType,
            String name, DurationModel durationModel, int fixedSeconds, BigDecimal secondsPerUnit,
            ResourceHoldPolicy resourceHoldPolicy, int maxSegments, int minSegmentSeconds,
            int resumeSetupSeconds, SegmentResourcePolicy segmentResourcePolicy,
            List<ResourceRequirement> requirements, long rowVersion)
    {
        public OperationPhase
        {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }

        public OperationPhase(String id, String operationSpecId, int phaseNo, PhaseType phaseType,
                String name, DurationModel durationModel, int fixedSeconds, BigDecimal secondsPerUnit,
                ResourceHoldPolicy resourceHoldPolicy, List<ResourceRequirement> requirements, long rowVersion)
        {
            this(id, operationSpecId, phaseNo, phaseType, name, durationModel, fixedSeconds, secondsPerUnit,
                    resourceHoldPolicy, 1, 0, 0, SegmentResourcePolicy.SAME_RESOURCES, requirements, rowVersion);
        }
    }

    public record OperationSpec(String id, String code, String name, OperationMode mode, String outputItemId,
            boolean interruptible, boolean qualityGateRequired, BigDecimal batchCapacity, String batchUomCode,
            String compatibilityRuleJson, String status, String remark, List<OperationPhase> phases,
            long rowVersion)
    {
        public OperationSpec
        {
            phases = phases == null ? List.of() : List.copyOf(phases);
        }
    }

    public record RouteVersion(String id, String itemId, String routeCode, String versionNo, String status,
            Instant effectiveFrom, Instant effectiveTo, String changeNote, String approvedBy, Instant approvedAt,
            long rowVersion) { }

    public record RouteNode(String id, String routeVersionId, String operationSpecId, String nodeCode,
            String nodeName, int displayOrder, BigDecimal quantityMultiplier, boolean terminal, long rowVersion) { }

    public record RouteEdge(String id, String routeVersionId, String predecessorNodeId, String successorNodeId,
            DependencyType dependencyType, BigDecimal thresholdQty, BigDecimal thresholdRatio,
            BigDecimal transferBatchQty, int lagSeconds, boolean consumesOutput, long rowVersion) { }

    public record RouteGraph(RouteVersion route, List<RouteNode> nodes, List<RouteEdge> edges)
    {
        public RouteGraph
        {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    /** API 编辑图使用节点编码引用，服务端负责稳定映射到节点 ID。 */
    public record RouteNodeDraft(String id, String operationSpecId, String nodeCode, String nodeName,
            int displayOrder, BigDecimal quantityMultiplier, boolean terminal) { }

    public record RouteEdgeDraft(String id, String predecessorNodeCode, String successorNodeCode,
            DependencyType dependencyType, BigDecimal thresholdQty, BigDecimal thresholdRatio,
            BigDecimal transferBatchQty, int lagSeconds, boolean consumesOutput) { }

    public record RouteGraphDraft(List<RouteNodeDraft> nodes, List<RouteEdgeDraft> edges)
    {
        public RouteGraphDraft
        {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    public record RouteValidation(String routeVersionId, boolean publishable, List<ApsValidationIssue> issues)
    {
        public RouteValidation
        {
            issues = List.copyOf(issues);
        }
    }

    public record RouteMigrationDiff(String orderLineId, String currentRouteVersionId, String targetRouteVersionId,
            List<String> addedNodes, List<String> removedNodes, List<String> changedOperations,
            List<String> addedEdges, List<String> removedEdges, boolean confirmationRequired)
    {
    }
}
