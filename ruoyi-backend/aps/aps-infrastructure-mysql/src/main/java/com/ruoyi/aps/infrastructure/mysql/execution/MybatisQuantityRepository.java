package com.ruoyi.aps.infrastructure.mysql.execution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ruoyi.aps.application.execution.ExecutionCatalog.DispositionType;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OutputLot;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ProductionReport;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QualityStatus;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QuantityEvent;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReportTarget;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReleasedSupply;
import com.ruoyi.aps.application.execution.ExecutionCatalog.MaterialTarget;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReservedAllocation;
import com.ruoyi.aps.application.execution.QuantityRepository;
import com.ruoyi.aps.domain.quantity.ProductionReportQuantities;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsQuantityMapper;
import com.ruoyi.aps.infrastructure.mysql.support.ApsRowMapperSupport;

/** MyBatis M27～M29 适配器。 */
public final class MybatisQuantityRepository extends ApsRowMapperSupport implements QuantityRepository
{
    private final ApsQuantityMapper mapper;

    public MybatisQuantityRepository(ApsQuantityMapper mapper) { this.mapper = mapper; }

    @Override public Optional<ProductionReport> findReportByRequestId(String id) { return Optional.ofNullable(mapper.findReportByRequestId(id)).map(this::report); }
    @Override public Optional<ProductionReport> findReportForUpdate(String id) { return Optional.ofNullable(mapper.findReportForUpdate(id)).map(this::report); }
    @Override public Optional<ReportTarget> findReportTargetForUpdate(String runId, String memberId, String taskId)
    {
        return Optional.ofNullable(mapper.findReportTargetForUpdate(runId, memberId, taskId)).map(row ->
                new ReportTarget(text(row, "plan_job_id"), text(row, "execution_run_id"),
                        text(row, "plan_job_member_id"), text(row, "task_id"), text(row, "production_lot_id"),
                        text(row, "item_id"), decimal(row, "member_qty"), decimal(row, "task_qty"),
                        text(row, "uom_code"), bool(row, "quality_gate_required")));
    }
    @Override public BigDecimal effectiveProcessedForRun(String id) { return mapper.effectiveProcessedForRun(id); }
    @Override public BigDecimal effectiveProcessedForMember(String runId, String memberId) { return mapper.effectiveProcessedForMember(runId, memberId); }
    @Override public boolean isReportCorrected(String id) { return mapper.isReportCorrected(id) > 0; }
    @Override public void insertReport(ProductionReport v, String actor) { mapper.insertReport(v, v.reportType().name(), actor); }
    @Override public List<ProductionReport> listReports(String id) { return mapper.listReports(id).stream().map(this::report).toList(); }
    @Override public Optional<OutputLot> findOutputLotForUpdate(String id) { return Optional.ofNullable(mapper.findOutputLotForUpdate(id)).map(this::lot); }
    @Override public List<OutputLot> listOutputLotsByReport(String id) { return mapper.listOutputLotsByReport(id).stream().map(this::lot).toList(); }
    @Override public List<OutputLot> listOutputLotsByRun(String id) { return mapper.listOutputLotsByRun(id).stream().map(this::lot).toList(); }
    @Override public void insertOutputLot(OutputLot v, String actor) { mapper.insertOutputLot(v, v.qualityStatus().name(), v.dispositionType().name(), actor); }
    @Override public int updateOutputLotProjection(OutputLot v, long expected, String actor) { return mapper.updateOutputLotProjection(v, expected, v.qualityStatus().name(), v.dispositionType().name(), actor); }
    @Override public Optional<QuantityEvent> findEventByRequestId(String id) { return Optional.ofNullable(mapper.findEventByRequestId(id)).map(this::event); }
    @Override public Optional<QuantityEvent> findEventForUpdate(String id) { return Optional.ofNullable(mapper.findEventForUpdate(id)).map(this::event); }
    @Override public List<QuantityEvent> listEvents(String id) { return mapper.listEvents(id).stream().map(this::event).toList(); }
    @Override public void insertEvent(QuantityEvent v, String actor)
    {
        requireDatabaseShape(v);
        mapper.insertEvent(v, v.eventType().name(), v.fromBucket() == null ? null : v.fromBucket().name(),
                v.toBucket() == null ? null : v.toBucket().name(), actor);
    }
    @Override public List<ReleasedSupply> listReleasedSuppliesForTasks(List<String> taskIds)
    {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        return mapper.listReleasedSuppliesForTasks(taskIds).stream().map(row -> new ReleasedSupply(
                text(row, "output_lot_id"), text(row, "item_id"), text(row, "source_task_id"),
                instant(row.get("available_at")), decimal(row, "quantity"), text(row, "uom_code"))).toList();
    }
    @Override public Optional<MaterialTarget> findMaterialTargetForUpdate(String id)
    {
        return Optional.ofNullable(mapper.findMaterialTargetForUpdate(id)).map(row -> new MaterialTarget(
                text(row, "id"), text(row, "target_task_id"), text(row, "item_id"),
                nullable(row, "source_task_id"), decimal(row, "required_qty"), text(row, "uom_code"),
                text(row, "material_status"), number(row, "row_version").longValue()));
    }
    @Override public BigDecimal allocatedForDemand(String id) { return mapper.allocatedForDemand(id); }
    @Override public BigDecimal reservedForDemandAndLot(String id, String lotId)
    {
        return mapper.reservedForDemandAndLot(id, lotId);
    }
    @Override public List<ReservedAllocation> listReservedAllocations(String lotId)
    {
        return mapper.listReservedAllocations(lotId).stream().map(row -> new ReservedAllocation(
                text(row, "material_demand_id"), text(row, "target_task_id"), decimal(row, "quantity"))).toList();
    }
    @Override public void refreshMaterialDemandStatus(String id, String actor)
    {
        mapper.refreshMaterialDemandStatus(id, actor);
    }

    private ProductionReport report(Map<String, Object> row)
    {
        ProductionReportQuantities quantities = new ProductionReportQuantities(decimal(row, "processed_qty"),
                decimal(row, "good_qty"), decimal(row, "pending_qty"), decimal(row, "rejected_qty"),
                decimal(row, "scrap_qty"), decimal(row, "transferred_qty"));
        return new ProductionReport(text(row, "id"), text(row, "plan_job_id"), text(row, "execution_run_id"),
                text(row, "plan_job_member_id"), text(row, "task_id"),
                com.ruoyi.aps.application.execution.ExecutionCatalog.ReportType.valueOf(text(row, "report_type")),
                instant(row.get("reported_at")), quantities, text(row, "uom_code"), nullable(row, "defect_reason"),
                nullable(row, "correction_of_id"), text(row, "request_id"), nullable(row, "operator_user_id"),
                number(row, "row_version").longValue());
    }

    private OutputLot lot(Map<String, Object> row)
    {
        return new OutputLot(text(row, "id"), text(row, "production_lot_id"), text(row, "source_task_id"),
                text(row, "source_report_id"), text(row, "item_id"), nullable(row, "source_output_lot_id"),
                text(row, "output_lot_no"), QualityStatus.valueOf(text(row, "quality_status")),
                decimal(row, "total_qty"), decimal(row, "available_qty"), decimal(row, "reserved_qty"),
                decimal(row, "consumed_qty"), decimal(row, "scrapped_qty"), text(row, "uom_code"),
                DispositionType.valueOf(text(row, "disposition_type")), nullable(row, "disposition_reason"),
                nullable(row, "approved_by"), instant(row.get("approved_at")),
                number(row, "row_version").longValue());
    }

    private QuantityEvent event(Map<String, Object> row)
    {
        return new QuantityEvent(text(row, "id"), text(row, "output_lot_id"), text(row, "item_id"),
                nullable(row, "material_demand_id"), nullable(row, "execution_run_id"),
                nullable(row, "production_report_id"), nullable(row, "target_task_id"),
                nullable(row, "reversal_of_id"),
                com.ruoyi.aps.domain.quantity.QuantityLedger.EventType.valueOf(text(row, "event_type")),
                bucket(row, "from_bucket"), bucket(row, "to_bucket"), decimal(row, "quantity"),
                text(row, "uom_code"), text(row, "request_id"), instant(row.get("occurred_at")),
                nullable(row, "actor_user_id"), nullable(row, "reason"));
    }

    private com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket bucket(Map<String, Object> row, String key)
    {
        String value = nullable(row, key);
        return value == null ? null : com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.valueOf(value);
    }

    /** 与 ck_quantity_event_shape 保持同形，避免只得到没有字段上下文的数据库约束异常。 */
    private void requireDatabaseShape(QuantityEvent event)
    {
        boolean noDemand = event.materialDemandId() == null && event.targetTaskId() == null;
        boolean noExecution = event.executionRunId() == null && event.productionReportId() == null;
        boolean valid = switch (event.eventType())
        {
            case PRODUCE -> noDemand && event.executionRunId() != null && event.productionReportId() != null
                    && event.fromBucket() == null && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.PENDING_QUALITY;
            case QUALITY_RELEASE -> noDemand && noExecution
                    && event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.PENDING_QUALITY
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE;
            case RESERVE -> event.materialDemandId() != null && event.targetTaskId() != null && noExecution
                    && event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.RESERVED;
            case UNRESERVE -> event.materialDemandId() != null && event.targetTaskId() != null && noExecution
                    && event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.RESERVED
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE;
            case CONSUME -> event.materialDemandId() != null && event.targetTaskId() != null
                    && event.executionRunId() != null && event.productionReportId() == null
                    && (event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE
                    || event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.RESERVED)
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.CONSUMED;
            case TRANSFER -> event.materialDemandId() != null && event.targetTaskId() != null && noExecution
                    && event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.TRANSFERRED;
            case SCRAP -> noDemand && noExecution
                    && (event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.PENDING_QUALITY
                    || event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.AVAILABLE
                    || event.fromBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.RESERVED)
                    && event.toBucket() == com.ruoyi.aps.domain.quantity.QuantityLedger.Bucket.SCRAPPED;
            case ADJUST -> noDemand && noExecution && event.fromBucket() != null && event.toBucket() != null
                    && event.fromBucket() != event.toBucket() && event.reason() != null;
            case REVERSE -> noDemand && noExecution
                    && (event.fromBucket() != null || event.toBucket() != null) && event.reason() != null;
        };
        if (!valid)
        {
            throw new IllegalArgumentException("数量事件不符合数据库形状约束：" + event);
        }
    }
}
