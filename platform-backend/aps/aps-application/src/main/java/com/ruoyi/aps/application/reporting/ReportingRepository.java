package com.ruoyi.aps.application.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.ruoyi.aps.application.resource.ResourceAccessScope;

/** M01～M29 报表查询端口；适配器必须在 SQL 内执行车间范围裁剪。 */
public interface ReportingRepository
{
    Optional<PlanReference> findDailyBaseline(LocalDate businessDate);
    Optional<PlanReference> findCurrentPublished();

    List<PlanSegmentFact> listPlanSegmentFacts(ResourceAccessScope scope, String planVersionId,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId);
    List<ActualOccupancyFact> listActualOccupancyFacts(ResourceAccessScope scope,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId);
    List<ProductionReportFact> listProductionReportFacts(ResourceAccessScope scope,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId);
    List<QuantityFact> listQuantityFacts(ResourceAccessScope scope,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId);

    List<PersonFact> listPeople(ResourceAccessScope scope, Instant fromAt, Instant toAt,
            String workshopId, String workCenterId);
    List<AvailabilityFact> listAvailability(ResourceAccessScope scope, Instant fromAt, Instant toAt,
            String workshopId, String workCenterId);
    List<PlannedLaborFact> listPlannedLabor(ResourceAccessScope scope, String planVersionId,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId, String skillCode);
    List<UnplannedLaborFact> listUnplannedLabor(ResourceAccessScope scope, String planVersionId,
            String workshopId, String workCenterId, String skillCode);

    List<OrderTaskFact> listOrderTaskFacts(ResourceAccessScope scope, String planVersionId, String orderId);

    record PlanReference(String id, String name, Instant publishedAt, LocalDate dailyBaselineDate) { }

    record TaskIdentity(String workshopId, String workshopCode, String workshopName,
            String workCenterId, String workCenterCode, String workCenterName,
            String operationSpecId, String operationCode, String operationName,
            String orderId, String orderNo, String orderLineId, int lineNo,
            String itemId, String itemCode, String itemName,
            String productionLotId, String lotNo, String taskId, String taskCode, String taskName,
            String taskStatus, String uomCode, Instant promisedAt) { }

    record PlanSegmentFact(TaskIdentity identity, String planVersionId, String planJobId,
            String planSegmentId, String allocationId, String allocationRole, String resourceId,
            Instant startAt, Instant endAt, Instant releaseAt, BigDecimal releaseQty,
            BigDecimal memberQty, BigDecimal jobQty) { }

    record ActualOccupancyFact(TaskIdentity identity, String occupancyId, String resourceId,
            String resourceType, Instant startAt, Instant endAt, BigDecimal memberQty,
            BigDecimal jobQty) { }

    record ProductionReportFact(TaskIdentity identity, String reportId, Instant occurredAt,
            BigDecimal processedQty, BigDecimal rejectedQty, BigDecimal scrapQty,
            BigDecimal transferredQty) { }

    record QuantityFact(TaskIdentity identity, String eventId, String eventType, String originalEventType,
            String fromBucket, String toBucket, Instant occurredAt, BigDecimal quantity) { }

    record PersonFact(String resourceId, String resourceCode, String resourceName,
            String workshopId, String workshopCode, String workshopName,
            String workCenterId, String workCenterCode, String workCenterName, String teamName,
            List<SkillQualification> skillQualifications)
    {
        public List<String> skillCodes()
        {
            return skillQualifications.stream().map(SkillQualification::skillCode).distinct().sorted().toList();
        }
    }

    record SkillQualification(String skillCode, Instant validFrom, Instant validTo) { }

    record AvailabilityFact(String resourceId, String windowType, Instant startAt, Instant endAt,
            BigDecimal capacityRatio) { }

    record PlannedLaborFact(String segmentId, String allocationId, String resourceId, String skillCode,
            Instant startAt, Instant endAt) { }

    record UnplannedLaborFact(String taskId, String skillCode, int requiredPeople, long requiredSeconds) { }

    record OrderTaskFact(String orderId, String orderNo, String orderStatus, Instant orderPromisedAt,
            String orderLineId, int lineNo, String lineStatus, Instant linePromisedAt,
            String itemId, String itemCode, String itemName, BigDecimal demandQty, String uomCode,
            String taskId, String taskCode, String taskStatus, boolean terminalTask,
            Instant plannedEndAt, BigDecimal terminalReleasedQty, Instant terminalReleasedAt,
            int unavailableMaterialCount, int qualityHoldCount, boolean missingResource,
            boolean missingDuration, int hiddenScopeTaskCount) { }
}
