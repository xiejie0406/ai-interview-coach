package com.ruoyi.aps.application.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** IMP-10 报表读模型；所有数量都保留原业务单位，不做跨单位合计。 */
public final class ReportingCatalog
{
    private ReportingCatalog() { }

    public record ReportMetadata(String siteCode, String zoneId, Instant fromAt, Instant toAt,
            String baselinePlanVersionId, String currentPlanVersionId, Instant dataCutoffAt, Instant generatedAt)
    { }

    public record DailyProductionReport(LocalDate businessDate, ReportMetadata metadata,
            List<DailyTaskRow> rows)
    {
        public DailyProductionReport { rows = List.copyOf(rows); }
    }

    public record DailyTaskRow(String workshopId, String workshopCode, String workshopName,
            String workCenterId, String workCenterCode, String workCenterName,
            String operationSpecId, String operationCode, String operationName,
            String orderId, String orderNo, String orderLineId, int lineNo,
            String itemId, String itemCode, String itemName,
            String productionLotId, String lotNo, String taskId, String taskCode, String taskName,
            String taskStatus, String uomCode, Instant promisedAt,
            Instant baselineStartAt, Instant baselineEndAt, BigDecimal baselinePlannedQty,
            BigDecimal baselinePersonHours, BigDecimal baselineMachineHours,
            Instant currentStartAt, Instant currentEndAt, BigDecimal currentPlannedQty,
            BigDecimal currentPersonHours, BigDecimal currentMachineHours,
            BigDecimal actualProcessedQty, BigDecimal actualGoodQty, BigDecimal rejectedQty,
            BigDecimal scrapQty, BigDecimal transferredQty,
            BigDecimal actualPersonHours, BigDecimal actualMachineHours,
            BigDecimal achievementRatio, BigDecimal carryoverQty, boolean delayed,
            List<String> plannedPersonResourceIds, List<String> plannedMachineResourceIds,
            List<String> actualPersonResourceIds, List<String> actualMachineResourceIds,
            List<String> reasonCodes)
    {
        public DailyTaskRow
        {
            plannedPersonResourceIds = List.copyOf(plannedPersonResourceIds);
            plannedMachineResourceIds = List.copyOf(plannedMachineResourceIds);
            actualPersonResourceIds = List.copyOf(actualPersonResourceIds);
            actualMachineResourceIds = List.copyOf(actualMachineResourceIds);
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    public record LaborCapacityReport(ReportMetadata metadata, List<PersonCapacityRow> people,
            List<SkillCapacityRow> skills, String aggregationNotice)
    {
        public LaborCapacityReport
        {
            people = List.copyOf(people);
            skills = List.copyOf(skills);
        }
    }

    public record PersonCapacityRow(String resourceId, String resourceCode, String resourceName,
            String workshopId, String workshopCode, String workshopName,
            String workCenterId, String workCenterCode, String workCenterName, String teamName,
            List<String> skillCodes, BigDecimal availableHours, BigDecimal scheduledHours,
            BigDecimal remainingHours, boolean overloaded)
    {
        public PersonCapacityRow { skillCodes = List.copyOf(skillCodes); }
    }

    public record SkillCapacityRow(String skillCode, BigDecimal availablePotentialHours,
            BigDecimal scheduledHours, BigDecimal unplannedRequiredHours,
            int peakRequiredPeople, int peakQualifiedPeople, int peakShortagePeople,
            Instant peakAt, List<String> reasonCodes)
    {
        public SkillCapacityRow { reasonCodes = List.copyOf(reasonCodes); }
    }

    public record OrderDeliveryReport(ReportMetadata metadata, List<OrderDeliveryRow> orders)
    {
        public OrderDeliveryReport { orders = List.copyOf(orders); }
    }

    public record OrderDeliveryRow(String orderId, String orderNo, String orderStatus,
            Instant promisedAt, String etaState, Instant expectedProductionAt, Instant knownLowerBoundAt,
            Long delaySeconds, boolean productionCompleted, boolean orderClosed,
            List<DeliveryReason> reasons, List<OrderLineDeliveryRow> lines)
    {
        public OrderDeliveryRow
        {
            reasons = List.copyOf(reasons);
            lines = List.copyOf(lines);
        }
    }

    public record OrderLineDeliveryRow(String orderLineId, int lineNo, String itemId, String itemCode,
            String itemName, String uomCode, BigDecimal demandQty, BigDecimal releasedTerminalQty,
            String lineStatus, Instant promisedAt, String etaState, Instant expectedProductionAt,
            Instant knownLowerBoundAt, List<DeliveryReason> reasons)
    {
        public OrderLineDeliveryRow { reasons = List.copyOf(reasons); }
    }

    public record DeliveryReason(String code, String objectType, String objectId, String detail) { }
}
