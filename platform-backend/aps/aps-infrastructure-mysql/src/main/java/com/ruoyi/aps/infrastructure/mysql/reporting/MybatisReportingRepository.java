package com.ruoyi.aps.infrastructure.mysql.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ruoyi.aps.application.reporting.ReportingRepository;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsReportingMapper;
import com.ruoyi.aps.infrastructure.mysql.support.ApsRowMapperSupport;

/** MyBatis 报表端口实现；仅把数据库事实转换成持久化中立记录。 */
public final class MybatisReportingRepository extends ApsRowMapperSupport implements ReportingRepository
{
    private final ApsReportingMapper mapper;

    public MybatisReportingRepository(ApsReportingMapper mapper) { this.mapper = mapper; }

    @Override public Optional<PlanReference> findDailyBaseline(LocalDate businessDate)
    {
        return Optional.ofNullable(mapper.findDailyBaseline(businessDate)).map(this::plan);
    }

    @Override public Optional<PlanReference> findCurrentPublished()
    {
        return Optional.ofNullable(mapper.findCurrentPublished()).map(this::plan);
    }

    @Override public List<PlanSegmentFact> listPlanSegmentFacts(ResourceAccessScope scope, String planVersionId,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId)
    {
        return mapper.listPlanSegmentFacts(scope.actorUserId(), scope.allWorkshops(), planVersionId, fromAt, toAt,
                workshopId, workCenterId).stream().map(row -> new PlanSegmentFact(identity(row),
                        text(row, "plan_version_id"), text(row, "plan_job_id"), text(row, "plan_segment_id"),
                        nullable(row, "allocation_id"), nullable(row, "allocation_role"),
                        nullable(row, "resource_id"), instant(row.get("start_at")), instant(row.get("end_at")),
                        instant(row.get("release_at")), nullableDecimal(row, "release_qty"),
                        decimal(row, "member_qty"), decimal(row, "job_qty"))).toList();
    }

    @Override public List<ActualOccupancyFact> listActualOccupancyFacts(ResourceAccessScope scope,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId)
    {
        return mapper.listActualOccupancyFacts(scope.actorUserId(), scope.allWorkshops(), fromAt, toAt,
                workshopId, workCenterId).stream().map(row -> new ActualOccupancyFact(identity(row),
                        text(row, "occupancy_id"), text(row, "resource_id"), text(row, "resource_type"),
                        instant(row.get("start_at")), instant(row.get("end_at")), decimal(row, "member_qty"),
                        decimal(row, "job_qty"))).toList();
    }

    @Override public List<ProductionReportFact> listProductionReportFacts(ResourceAccessScope scope,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId)
    {
        return mapper.listProductionReportFacts(scope.actorUserId(), scope.allWorkshops(), fromAt, toAt,
                workshopId, workCenterId).stream().map(row -> new ProductionReportFact(identity(row),
                        text(row, "report_id"), instant(row.get("occurred_at")), decimal(row, "processed_qty"),
                        decimal(row, "rejected_qty"), decimal(row, "scrap_qty"),
                        decimal(row, "transferred_qty"))).toList();
    }

    @Override public List<QuantityFact> listQuantityFacts(ResourceAccessScope scope, Instant fromAt,
            Instant toAt, String workshopId, String workCenterId)
    {
        return mapper.listQuantityFacts(scope.actorUserId(), scope.allWorkshops(), fromAt, toAt,
                workshopId, workCenterId).stream().map(row -> new QuantityFact(identity(row),
                        text(row, "event_id"), text(row, "event_type"), nullable(row, "original_event_type"),
                        nullable(row, "from_bucket"), nullable(row, "to_bucket"),
                        instant(row.get("occurred_at")), decimal(row, "quantity"))).toList();
    }

    @Override public List<PersonFact> listPeople(ResourceAccessScope scope, Instant fromAt, Instant toAt,
            String workshopId, String workCenterId)
    {
        Map<String, Map<String, Object>> people = new LinkedHashMap<>();
        Map<String, List<SkillQualification>> skills = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.listPeople(scope.actorUserId(), scope.allWorkshops(),
                fromAt, toAt, workshopId, workCenterId))
        {
            String resourceId = text(row, "resource_id");
            people.putIfAbsent(resourceId, row);
            String skillCode = nullable(row, "skill_code");
            if (skillCode != null)
                skills.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(new SkillQualification(
                        skillCode, instant(row.get("skill_valid_from")), instant(row.get("skill_valid_to"))));
        }
        return people.entrySet().stream().map(entry -> {
            Map<String, Object> row = entry.getValue();
            return new PersonFact(entry.getKey(), text(row, "resource_code"), text(row, "resource_name"),
                    text(row, "workshop_id"), text(row, "workshop_code"), text(row, "workshop_name"),
                    nullable(row, "work_center_id"), nullable(row, "work_center_code"),
                    nullable(row, "work_center_name"), nullable(row, "team_name"),
                    List.copyOf(skills.getOrDefault(entry.getKey(), List.of())));
        }).toList();
    }

    @Override public List<AvailabilityFact> listAvailability(ResourceAccessScope scope, Instant fromAt,
            Instant toAt, String workshopId, String workCenterId)
    {
        return mapper.listAvailability(scope.actorUserId(), scope.allWorkshops(), fromAt, toAt, workshopId,
                workCenterId).stream().map(row -> new AvailabilityFact(text(row, "resource_id"),
                        text(row, "window_type"), instant(row.get("start_at")), instant(row.get("end_at")),
                        decimal(row, "capacity_ratio"))).toList();
    }

    @Override public List<PlannedLaborFact> listPlannedLabor(ResourceAccessScope scope, String planVersionId,
            Instant fromAt, Instant toAt, String workshopId, String workCenterId, String skillCode)
    {
        return mapper.listPlannedLabor(scope.actorUserId(), scope.allWorkshops(), planVersionId, fromAt, toAt,
                workshopId, workCenterId, skillCode).stream().map(row -> new PlannedLaborFact(
                        text(row, "segment_id"), text(row, "allocation_id"), text(row, "resource_id"),
                        text(row, "skill_code"),
                        instant(row.get("start_at")), instant(row.get("end_at")))).toList();
    }

    @Override public List<UnplannedLaborFact> listUnplannedLabor(ResourceAccessScope scope, String planVersionId,
            String workshopId, String workCenterId, String skillCode)
    {
        return mapper.listUnplannedLabor(scope.actorUserId(), scope.allWorkshops(), planVersionId, workshopId,
                workCenterId, skillCode).stream().map(row -> new UnplannedLaborFact(text(row, "task_id"),
                        text(row, "skill_code"), number(row, "required_people").intValue(),
                        number(row, "required_seconds").longValue())).toList();
    }

    @Override public List<OrderTaskFact> listOrderTaskFacts(ResourceAccessScope scope, String planVersionId,
            String orderId)
    {
        return mapper.listOrderTaskFacts(scope.actorUserId(), scope.allWorkshops(), planVersionId, orderId).stream()
                .map(row -> new OrderTaskFact(text(row, "order_id"), text(row, "order_no"),
                        text(row, "order_status"), instant(row.get("order_promised_at")),
                        text(row, "order_line_id"), number(row, "line_no").intValue(), text(row, "line_status"),
                        instant(row.get("line_promised_at")), text(row, "item_id"), text(row, "item_code"),
                        text(row, "item_name"), decimal(row, "demand_qty"), text(row, "uom_code"),
                        text(row, "task_id"), text(row, "task_code"), text(row, "task_status"),
                        bool(row, "terminal_task"), instant(row.get("planned_end_at")),
                        decimal(row, "terminal_released_qty"), instant(row.get("terminal_released_at")),
                        number(row, "unavailable_material_count").intValue(),
                        number(row, "quality_hold_count").intValue(), bool(row, "missing_resource"),
                        bool(row, "missing_duration"), number(row, "hidden_scope_task_count").intValue())).toList();
    }

    private PlanReference plan(Map<String, Object> row)
    {
        Object date = row.get("daily_baseline_date");
        LocalDate baseline = date instanceof LocalDate value ? value
                : date instanceof java.sql.Date value ? value.toLocalDate() : null;
        return new PlanReference(text(row, "id"), text(row, "version_name"), instant(row.get("published_at")),
                baseline);
    }

    private TaskIdentity identity(Map<String, Object> row)
    {
        return new TaskIdentity(nullable(row, "workshop_id"), nullable(row, "workshop_code"),
                nullable(row, "workshop_name"), nullable(row, "work_center_id"),
                nullable(row, "work_center_code"), nullable(row, "work_center_name"),
                text(row, "operation_spec_id"), text(row, "operation_code"), text(row, "operation_name"),
                text(row, "order_id"), text(row, "order_no"), text(row, "order_line_id"),
                number(row, "line_no").intValue(), text(row, "item_id"), text(row, "item_code"),
                text(row, "item_name"), text(row, "production_lot_id"), text(row, "lot_no"),
                text(row, "task_id"), text(row, "task_code"), text(row, "task_name"),
                text(row, "task_status"), text(row, "uom_code"), instant(row.get("promised_at")));
    }

}
