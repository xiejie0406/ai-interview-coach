package com.ruoyi.aps.infrastructure.mysql.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** IMP-10 报表只读 SQL；所有明细查询必须携带 actorUserId/allScope。 */
public interface ApsReportingMapper
{
    Map<String, Object> findDailyBaseline(@Param("businessDate") LocalDate businessDate);
    Map<String, Object> findCurrentPublished();

    List<Map<String, Object>> listPlanSegmentFacts(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("planVersionId") String planVersionId,
            @Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt,
            @Param("workshopId") String workshopId, @Param("workCenterId") String workCenterId);
    List<Map<String, Object>> listActualOccupancyFacts(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt, @Param("workshopId") String workshopId,
            @Param("workCenterId") String workCenterId);
    List<Map<String, Object>> listProductionReportFacts(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt, @Param("workshopId") String workshopId,
            @Param("workCenterId") String workCenterId);
    List<Map<String, Object>> listQuantityFacts(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt, @Param("workshopId") String workshopId,
            @Param("workCenterId") String workCenterId);

    List<Map<String, Object>> listPeople(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt, @Param("workshopId") String workshopId,
            @Param("workCenterId") String workCenterId);
    List<Map<String, Object>> listAvailability(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt, @Param("workshopId") String workshopId,
            @Param("workCenterId") String workCenterId);
    List<Map<String, Object>> listPlannedLabor(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("planVersionId") String planVersionId,
            @Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt,
            @Param("workshopId") String workshopId, @Param("workCenterId") String workCenterId,
            @Param("skillCode") String skillCode);
    List<Map<String, Object>> listUnplannedLabor(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("planVersionId") String planVersionId,
            @Param("workshopId") String workshopId, @Param("workCenterId") String workCenterId,
            @Param("skillCode") String skillCode);

    List<Map<String, Object>> listOrderTaskFacts(@Param("actorUserId") String actorUserId,
            @Param("allScope") boolean allScope, @Param("planVersionId") String planVersionId,
            @Param("orderId") String orderId);
}
