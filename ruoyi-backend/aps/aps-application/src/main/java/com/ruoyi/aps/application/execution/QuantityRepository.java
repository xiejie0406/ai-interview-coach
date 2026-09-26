package com.ruoyi.aps.application.execution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OutputLot;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ProductionReport;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QuantityEvent;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReportTarget;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReleasedSupply;
import com.ruoyi.aps.application.execution.ExecutionCatalog.MaterialTarget;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReservedAllocation;

/** M27～M29 追加式事实及 M28 投影端口；调用方负责把写入放在同一事务。 */
public interface QuantityRepository
{
    Optional<ProductionReport> findReportByRequestId(String requestId);
    Optional<ProductionReport> findReportForUpdate(String reportId);
    Optional<ReportTarget> findReportTargetForUpdate(String executionRunId, String planJobMemberId, String taskId);
    BigDecimal effectiveProcessedForRun(String executionRunId);
    BigDecimal effectiveProcessedForMember(String executionRunId, String planJobMemberId);
    boolean isReportCorrected(String reportId);
    void insertReport(ProductionReport report, String actor);
    List<ProductionReport> listReports(String executionRunId);
    Optional<OutputLot> findOutputLotForUpdate(String outputLotId);
    List<OutputLot> listOutputLotsByReport(String reportId);
    List<OutputLot> listOutputLotsByRun(String executionRunId);
    void insertOutputLot(OutputLot outputLot, String actor);
    int updateOutputLotProjection(OutputLot outputLot, long expectedRowVersion, String actor);
    Optional<QuantityEvent> findEventByRequestId(String requestId);
    Optional<QuantityEvent> findEventForUpdate(String quantityEventId);
    List<QuantityEvent> listEvents(String outputLotId);
    void insertEvent(QuantityEvent event, String actor);
    default List<ReleasedSupply> listReleasedSuppliesForTasks(List<String> taskIds) { return List.of(); }
    default Optional<MaterialTarget> findMaterialTargetForUpdate(String materialDemandId) { return Optional.empty(); }
    default BigDecimal allocatedForDemand(String materialDemandId) { return BigDecimal.ZERO; }
    default BigDecimal reservedForDemandAndLot(String materialDemandId, String outputLotId) { return BigDecimal.ZERO; }
    default List<ReservedAllocation> listReservedAllocations(String outputLotId) { return List.of(); }
    default void refreshMaterialDemandStatus(String materialDemandId, String actor) { }
}
