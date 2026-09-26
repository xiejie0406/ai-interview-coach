package com.ruoyi.aps.infrastructure.mysql.resource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.resource.ResourceCatalog.Availability;
import com.ruoyi.aps.application.resource.ResourceCatalog.Resource;
import com.ruoyi.aps.application.resource.ResourceCatalog.Skill;
import com.ruoyi.aps.application.resource.ResourceCatalog.WorkCenter;
import com.ruoyi.aps.application.resource.ResourceCatalog.Workshop;
import com.ruoyi.aps.application.resource.ResourceRepository;
import com.ruoyi.aps.domain.resource.AvailabilityType;
import com.ruoyi.aps.domain.resource.ResourceType;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsResourceMapper;

public final class MybatisResourceRepository implements ResourceRepository
{
    private final ApsResourceMapper mapper;

    public MybatisResourceRepository(ApsResourceMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override public List<Workshop> listWorkshops(ResourceAccessScope scope) { return mapper.listWorkshops(scope).stream().map(this::workshop).toList(); }
    @Override public Optional<Workshop> findWorkshop(ResourceAccessScope scope, String id) { return Optional.ofNullable(mapper.findWorkshop(scope, id)).map(this::workshop); }
    @Override public void insertWorkshop(Workshop value, String actor) { mapper.insertWorkshop(value, actor); }
    @Override public int updateWorkshop(Workshop value, String actor) { return mapper.updateWorkshop(value, actor); }
    @Override public List<WorkCenter> listWorkCenters(ResourceAccessScope scope, String workshopId) { return mapper.listWorkCenters(scope, workshopId).stream().map(this::center).toList(); }
    @Override public Optional<WorkCenter> findWorkCenter(ResourceAccessScope scope, String id) { return Optional.ofNullable(mapper.findWorkCenter(scope, id)).map(this::center); }
    @Override public void insertWorkCenter(WorkCenter value, String actor) { mapper.insertWorkCenter(value, actor); }
    @Override public int updateWorkCenter(WorkCenter value, String actor) { return mapper.updateWorkCenter(value, actor); }
    @Override public List<Resource> listResources(ResourceAccessScope scope, String workshopId, String workCenterId) { return mapper.listResources(scope, workshopId, workCenterId).stream().map(this::resource).toList(); }
    @Override public Optional<Resource> findResource(ResourceAccessScope scope, String id) { return Optional.ofNullable(mapper.findResource(scope, id)).map(this::resource); }
    @Override public void insertResource(Resource value, String actor) { mapper.insertResource(value, actor); }
    @Override public int updateResource(Resource value, String actor) { return mapper.updateResource(value, actor); }
    @Override public List<Skill> listSkills(ResourceAccessScope scope, String resourceId) { return mapper.listSkills(scope, resourceId).stream().map(this::skill).toList(); }
    @Override public void insertSkill(Skill value, String actor) { mapper.insertSkill(value, actor); }
    @Override public int updateSkill(Skill value, String actor) { return mapper.updateSkill(value, actor); }
    @Override public List<Availability> listAvailability(ResourceAccessScope scope, String resourceId) { return mapper.listAvailability(scope, resourceId).stream().map(this::availability).toList(); }
    @Override public void insertAvailability(Availability value, String actor) { mapper.insertAvailability(value, actor); }
    @Override public int updateAvailability(Availability value, String actor) { return mapper.updateAvailability(value, actor); }
    @Override public boolean hasPlanOrExecutionReference(ResourceAccessScope scope, String resourceId) { return Boolean.TRUE.equals(mapper.hasPlanOrExecutionReference(scope, resourceId)); }

    private Workshop workshop(Map<String, Object> row)
    {
        return new Workshop(text(row, "id"), text(row, "workshop_code"), text(row, "workshop_name"), nullable(row, "manager_user_id"),
                text(row, "status"), nullable(row, "remark"), number(row, "row_version").longValue());
    }

    private WorkCenter center(Map<String, Object> row)
    {
        return new WorkCenter(text(row, "id"), text(row, "workshop_id"), text(row, "center_code"), text(row, "center_name"),
                text(row, "center_type"), decimal(row, "concurrent_capacity"), text(row, "capacity_uom_code"),
                text(row, "status"), nullable(row, "remark"), number(row, "row_version").longValue());
    }

    private Resource resource(Map<String, Object> row)
    {
        return new Resource(text(row, "id"), text(row, "workshop_id"), nullable(row, "work_center_id"), text(row, "resource_code"),
                text(row, "resource_name"), ResourceType.valueOf(text(row, "resource_type")), nullable(row, "ruoyi_user_id"),
                nullable(row, "team_name"), decimal(row, "capacity_value"), text(row, "capacity_uom_code"), text(row, "status"),
                nullable(row, "remark"), number(row, "row_version").longValue());
    }

    private Skill skill(Map<String, Object> row)
    {
        return new Skill(text(row, "id"), text(row, "resource_id"), text(row, "skill_code"), text(row, "skill_name"),
                number(row, "skill_level").intValue(), instant(row.get("valid_from")), instant(row.get("valid_to")),
                nullable(row, "certificate_ref"), text(row, "status"), number(row, "row_version").longValue());
    }

    private Availability availability(Map<String, Object> row)
    {
        return new Availability(text(row, "id"), text(row, "resource_id"), AvailabilityType.valueOf(text(row, "window_type")),
                instant(row.get("start_at")), instant(row.get("end_at")), decimal(row, "capacity_ratio"), text(row, "source_type"),
                nullable(row, "source_ref"), nullable(row, "reason"), number(row, "row_version").longValue());
    }

    private String text(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
    private String nullable(Map<String, Object> row, String key) { return row.get(key) == null ? null : String.valueOf(row.get(key)); }
    private Number number(Map<String, Object> row, String key) { return (Number) row.get(key); }
    private BigDecimal decimal(Map<String, Object> row, String key) { return row.get(key) instanceof BigDecimal value ? value : new BigDecimal(String.valueOf(row.get(key))); }
    private Instant instant(Object value)
    {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalArgumentException("不支持的数据库时间类型: " + value.getClass().getName());
    }
}
