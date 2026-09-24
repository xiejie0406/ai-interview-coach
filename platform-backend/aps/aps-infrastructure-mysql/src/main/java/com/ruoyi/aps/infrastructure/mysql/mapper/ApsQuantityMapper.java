package com.ruoyi.aps.infrastructure.mysql.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OutputLot;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ProductionReport;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QuantityEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** M27～M29 追加事实与投影 SQL。 */
public interface ApsQuantityMapper
{
    @Select("SELECT * FROM aps_production_report WHERE request_id=#{requestId} FOR UPDATE")
    Map<String, Object> findReportByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM aps_production_report WHERE id=#{reportId} FOR UPDATE")
    Map<String, Object> findReportForUpdate(@Param("reportId") String reportId);

    @Select("SELECT r.plan_job_id,r.id AS execution_run_id,m.id AS plan_job_member_id,m.task_id," +
            "t.production_lot_id,l.item_id,m.planned_qty AS member_qty,t.task_qty,t.uom_code," +
            "o.quality_gate_required FROM aps_execution_run r " +
            "JOIN aps_plan_job_member m ON m.plan_job_id=r.plan_job_id " +
            "JOIN aps_task t ON t.id=m.task_id JOIN aps_production_lot l ON l.id=t.production_lot_id " +
            "JOIN aps_operation_spec o ON o.id=t.operation_spec_id " +
            "WHERE r.id=#{executionRunId} AND m.id=#{planJobMemberId} AND m.task_id=#{taskId} FOR UPDATE")
    Map<String, Object> findReportTargetForUpdate(@Param("executionRunId") String executionRunId,
            @Param("planJobMemberId") String planJobMemberId, @Param("taskId") String taskId);

    @Select("SELECT COALESCE(SUM(p.processed_qty),0) FROM aps_production_report p " +
            "WHERE p.execution_run_id=#{executionRunId} AND NOT EXISTS " +
            "(SELECT 1 FROM aps_production_report c WHERE c.correction_of_id=p.id)")
    BigDecimal effectiveProcessedForRun(@Param("executionRunId") String executionRunId);

    @Select("SELECT COALESCE(SUM(p.processed_qty),0) FROM aps_production_report p " +
            "WHERE p.execution_run_id=#{executionRunId} AND p.plan_job_member_id=#{planJobMemberId} " +
            "AND NOT EXISTS (SELECT 1 FROM aps_production_report c WHERE c.correction_of_id=p.id)")
    BigDecimal effectiveProcessedForMember(@Param("executionRunId") String executionRunId,
            @Param("planJobMemberId") String planJobMemberId);

    @Select("SELECT COUNT(*) FROM aps_production_report WHERE correction_of_id=#{reportId}")
    int isReportCorrected(@Param("reportId") String reportId);

    @Insert("INSERT INTO aps_production_report(id,plan_job_id,execution_run_id,plan_job_member_id,task_id," +
            "report_type,reported_at,processed_qty,good_qty,pending_qty,rejected_qty,scrap_qty,transferred_qty," +
            "uom_code,defect_reason,correction_of_id,request_id,operator_user_id,created_by,updated_by) " +
            "VALUES(#{v.id},#{v.planJobId},#{v.executionRunId},#{v.planJobMemberId},#{v.taskId},#{reportType}," +
            "#{v.reportedAt},#{v.quantities.processedQty},#{v.quantities.goodQty},#{v.quantities.pendingQty}," +
            "#{v.quantities.rejectedQty},#{v.quantities.scrapQty},#{v.quantities.transferredQty},#{v.uomCode}," +
            "#{v.defectReason},#{v.correctionOfId},#{v.requestId},#{v.operatorUserId},#{actor},#{actor})")
    void insertReport(@Param("v") ProductionReport report, @Param("reportType") String reportType,
            @Param("actor") String actor);

    @Select("SELECT * FROM aps_production_report WHERE execution_run_id=#{executionRunId} ORDER BY reported_at,id")
    List<Map<String, Object>> listReports(@Param("executionRunId") String executionRunId);

    @Select("SELECT * FROM aps_output_lot WHERE id=#{outputLotId} FOR UPDATE")
    Map<String, Object> findOutputLotForUpdate(@Param("outputLotId") String outputLotId);

    @Select("SELECT * FROM aps_output_lot WHERE source_report_id=#{reportId} ORDER BY output_lot_no,id FOR UPDATE")
    List<Map<String, Object>> listOutputLotsByReport(@Param("reportId") String reportId);

    @Select("SELECT l.* FROM aps_output_lot l JOIN aps_production_report p ON p.id=l.source_report_id " +
            "WHERE p.execution_run_id=#{executionRunId} ORDER BY p.reported_at,l.output_lot_no,l.id")
    List<Map<String, Object>> listOutputLotsByRun(@Param("executionRunId") String executionRunId);

    @Insert("INSERT INTO aps_output_lot(id,production_lot_id,source_task_id,source_report_id,item_id," +
            "source_output_lot_id,output_lot_no,quality_status,total_qty,available_qty,reserved_qty," +
            "consumed_qty,scrapped_qty,uom_code,disposition_type,disposition_reason,approved_by,approved_at," +
            "created_by,updated_by) VALUES(#{v.id},#{v.productionLotId},#{v.sourceTaskId},#{v.sourceReportId}," +
            "#{v.itemId},#{v.sourceOutputLotId},#{v.outputLotNo},#{qualityStatus},#{v.totalQty},#{v.availableQty}," +
            "#{v.reservedQty},#{v.consumedQty},#{v.scrappedQty},#{v.uomCode},#{dispositionType}," +
            "#{v.dispositionReason},#{v.approvedBy},#{v.approvedAt},#{actor},#{actor})")
    void insertOutputLot(@Param("v") OutputLot outputLot, @Param("qualityStatus") String qualityStatus,
            @Param("dispositionType") String dispositionType, @Param("actor") String actor);

    @Update("UPDATE aps_output_lot SET quality_status=#{qualityStatus},available_qty=#{v.availableQty}," +
            "reserved_qty=#{v.reservedQty},consumed_qty=#{v.consumedQty},scrapped_qty=#{v.scrappedQty}," +
            "disposition_type=#{dispositionType},disposition_reason=#{v.dispositionReason}," +
            "approved_by=#{v.approvedBy},approved_at=#{v.approvedAt},updated_by=#{actor},row_version=row_version+1 " +
            "WHERE id=#{v.id} AND row_version=#{expectedRowVersion}")
    int updateOutputLotProjection(@Param("v") OutputLot outputLot, @Param("expectedRowVersion") long expectedRowVersion,
            @Param("qualityStatus") String qualityStatus, @Param("dispositionType") String dispositionType,
            @Param("actor") String actor);

    @Select("SELECT * FROM aps_quantity_event WHERE request_id=#{requestId} FOR UPDATE")
    Map<String, Object> findEventByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM aps_quantity_event WHERE id=#{quantityEventId} FOR UPDATE")
    Map<String, Object> findEventForUpdate(@Param("quantityEventId") String quantityEventId);

    @Select("SELECT * FROM aps_quantity_event WHERE output_lot_id=#{outputLotId} ORDER BY created_at,id FOR UPDATE")
    List<Map<String, Object>> listEvents(@Param("outputLotId") String outputLotId);

    @Insert("INSERT INTO aps_quantity_event(id,output_lot_id,item_id,material_demand_id,execution_run_id," +
            "production_report_id,target_task_id,reversal_of_id,event_type,from_bucket,to_bucket,quantity," +
            "uom_code,request_id,occurred_at,actor_user_id,reason,created_by) VALUES(#{v.id},#{v.outputLotId}," +
            "#{v.itemId},#{v.materialDemandId},#{v.executionRunId},#{v.productionReportId},#{v.targetTaskId}," +
            "#{v.reversalOfId},#{eventType},#{fromBucket},#{toBucket},#{v.quantity},#{v.uomCode},#{v.requestId}," +
            "#{v.occurredAt},#{v.actorUserId},#{v.reason},#{actor})")
    void insertEvent(@Param("v") QuantityEvent event, @Param("eventType") String eventType,
            @Param("fromBucket") String fromBucket, @Param("toBucket") String toBucket,
            @Param("actor") String actor);

    @Select({"<script>",
            "SELECT l.id AS output_lot_id,l.item_id,l.source_task_id,l.available_qty AS quantity,l.uom_code,",
            "MIN(CASE WHEN e.event_type='QUALITY_RELEASE' THEN e.occurred_at END) AS available_at ",
            "FROM aps_output_lot l JOIN aps_quantity_event e ON e.output_lot_id=l.id ",
            "WHERE l.available_qty>0 AND l.quality_status&lt;&gt;'CLOSED' AND l.source_task_id IN ",
            "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "GROUP BY l.id,l.item_id,l.source_task_id,l.available_qty,l.uom_code ORDER BY l.source_task_id,l.id",
            "</script>"})
    List<Map<String, Object>> listReleasedSuppliesForTasks(@Param("taskIds") List<String> taskIds);

    @Select("SELECT id,target_task_id,item_id,source_task_id,required_qty,uom_code,material_status,row_version " +
            "FROM aps_material_demand WHERE id=#{id} FOR UPDATE")
    Map<String, Object> findMaterialTargetForUpdate(@Param("id") String id);

    @Select("SELECT COALESCE(SUM((CASE WHEN to_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN quantity ELSE 0 END)-(CASE WHEN from_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN quantity ELSE 0 END)),0) FROM aps_quantity_event " +
            "WHERE material_demand_id=#{id} AND NOT EXISTS (SELECT 1 FROM aps_quantity_event r " +
            "WHERE r.reversal_of_id=aps_quantity_event.id)")
    BigDecimal allocatedForDemand(@Param("id") String id);

    @Select("SELECT COALESCE(SUM((CASE WHEN to_bucket='RESERVED' THEN quantity ELSE 0 END)-" +
            "(CASE WHEN from_bucket='RESERVED' THEN quantity ELSE 0 END)),0) " +
            "FROM aps_quantity_event WHERE material_demand_id=#{id} AND output_lot_id=#{outputLotId} " +
            "AND NOT EXISTS (SELECT 1 FROM aps_quantity_event r WHERE r.reversal_of_id=aps_quantity_event.id)")
    BigDecimal reservedForDemandAndLot(@Param("id") String id, @Param("outputLotId") String outputLotId);

    @Select("SELECT material_demand_id,target_task_id," +
            "SUM((CASE WHEN to_bucket='RESERVED' THEN quantity ELSE 0 END)-" +
            "(CASE WHEN from_bucket='RESERVED' THEN quantity ELSE 0 END)) quantity " +
            "FROM aps_quantity_event WHERE output_lot_id=#{outputLotId} AND material_demand_id IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM aps_quantity_event r WHERE r.reversal_of_id=aps_quantity_event.id) " +
            "GROUP BY material_demand_id,target_task_id HAVING quantity>0 " +
            "ORDER BY material_demand_id FOR UPDATE")
    List<Map<String, Object>> listReservedAllocations(@Param("outputLotId") String outputLotId);

    @Update("UPDATE aps_material_demand d SET d.material_status=(CASE " +
            "WHEN (SELECT COALESCE(SUM((CASE WHEN e.to_bucket='CONSUMED' THEN e.quantity ELSE 0 END)-" +
            "(CASE WHEN e.from_bucket='CONSUMED' THEN e.quantity ELSE 0 END)),0) " +
            "FROM aps_quantity_event e WHERE e.material_demand_id=d.id AND NOT EXISTS " +
            "(SELECT 1 FROM aps_quantity_event r WHERE r.reversal_of_id=e.id))>=d.required_qty THEN 'CONSUMED' " +
            "WHEN (SELECT COALESCE(SUM((CASE WHEN e.to_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN e.quantity ELSE 0 END)-(CASE WHEN e.from_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN e.quantity ELSE 0 END)),0) FROM aps_quantity_event e " +
            "WHERE e.material_demand_id=d.id AND NOT EXISTS " +
            "(SELECT 1 FROM aps_quantity_event r WHERE r.reversal_of_id=e.id))>=d.required_qty THEN 'AVAILABLE' " +
            "WHEN (SELECT COALESCE(SUM((CASE WHEN e.to_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN e.quantity ELSE 0 END)-(CASE WHEN e.from_bucket IN ('RESERVED','CONSUMED','TRANSFERRED') " +
            "THEN e.quantity ELSE 0 END)),0) FROM aps_quantity_event e " +
            "WHERE e.material_demand_id=d.id AND NOT EXISTS " +
            "(SELECT 1 FROM aps_quantity_event r WHERE r.reversal_of_id=e.id))>0 " +
            "THEN 'PARTIAL' ELSE 'UNAVAILABLE' END)," +
            "d.updated_by=#{actor},d.row_version=d.row_version+1 WHERE d.id=#{id}")
    int refreshMaterialDemandStatus(@Param("id") String id, @Param("actor") String actor);
}
