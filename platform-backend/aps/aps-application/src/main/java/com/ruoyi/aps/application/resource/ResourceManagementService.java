package com.ruoyi.aps.application.resource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceCatalog.Availability;
import com.ruoyi.aps.application.resource.ResourceCatalog.Candidate;
import com.ruoyi.aps.application.resource.ResourceCatalog.ReadinessIssue;
import com.ruoyi.aps.application.resource.ResourceCatalog.Resource;
import com.ruoyi.aps.application.resource.ResourceCatalog.Skill;
import com.ruoyi.aps.application.resource.ResourceCatalog.WorkCenter;
import com.ruoyi.aps.application.resource.ResourceCatalog.Workshop;
import com.ruoyi.aps.domain.resource.AvailabilityType;
import com.ruoyi.aps.domain.resource.AvailabilityWindow;
import com.ruoyi.aps.domain.resource.ResourceCalendarService;
import com.ruoyi.aps.domain.resource.ResourceCalendarService.NetWindow;
import com.ruoyi.aps.domain.resource.ResourceSkill;
import com.ruoyi.aps.domain.resource.ResourceType;
import com.ruoyi.aps.domain.shared.UtcTimeWindow;

/** 组织、资源、技能和绝对日历的应用用例。 */
public final class ResourceManagementService
{
    private final ResourceRepository repository;
    private final ApsTransactionOperations transactions;
    private final ResourceCalendarService calendar = new ResourceCalendarService();

    public ResourceManagementService(ResourceRepository repository, ApsTransactionOperations transactions)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public List<Workshop> listWorkshops(ResourceAccessScope scope)
    {
        return repository.listWorkshops(scope);
    }

    public Workshop saveWorkshop(ResourceAccessScope scope, ApsAuditActor actor, Workshop input)
    {
        requireText(input.code(), "车间编码");
        requireText(input.name(), "车间名称");
        String manager = blankToNull(input.managerUserId());
        if (!scope.allWorkshops() && manager != null && !manager.equals(scope.actorUserId()))
        {
            throw forbidden();
        }
        Workshop value = new Workshop(idOrNew(input.id()), normalizeCode(input.code()), input.name().trim(),
                manager == null ? actor.userId() : manager, lifecycle(input.status()), blankToNull(input.remark()),
                input.rowVersion());
        return transactions.required(() -> {
            if (input.id() == null || input.id().isBlank())
            {
                repository.insertWorkshop(value, actor.userId());
                return value;
            }
            requireWorkshop(scope, value.id());
            requireUpdated(repository.updateWorkshop(value, actor.userId()));
            return new Workshop(value.id(), value.code(), value.name(), value.managerUserId(), value.status(),
                    value.remark(), value.rowVersion() + 1);
        });
    }

    public List<WorkCenter> listWorkCenters(ResourceAccessScope scope, String workshopId)
    {
        requireWorkshop(scope, workshopId);
        return repository.listWorkCenters(scope, workshopId);
    }

    public WorkCenter saveWorkCenter(ResourceAccessScope scope, ApsAuditActor actor, WorkCenter input)
    {
        requireWorkshop(scope, input.workshopId());
        requireText(input.code(), "工作中心编码");
        requireText(input.name(), "工作中心名称");
        positive(input.concurrentCapacity(), "并发容量");
        WorkCenter value = new WorkCenter(idOrNew(input.id()), input.workshopId(), normalizeCode(input.code()),
                input.name().trim(), enumText(input.centerType(), "MIXED", Set.of("MACHINE", "LABOR", "MIXED", "BATCH")),
                input.concurrentCapacity(), defaultText(input.capacityUomCode(), "COUNT").toUpperCase(Locale.ROOT),
                lifecycle(input.status()), blankToNull(input.remark()), input.rowVersion());
        return transactions.required(() -> {
            if (input.id() == null || input.id().isBlank())
            {
                repository.insertWorkCenter(value, actor.userId());
                return value;
            }
            WorkCenter current = repository.findWorkCenter(scope, input.id()).orElseThrow(this::notFound);
            if (!current.workshopId().equals(input.workshopId()))
            {
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "不能跨车间移动工作中心");
            }
            requireUpdated(repository.updateWorkCenter(value, actor.userId()));
            return new WorkCenter(value.id(), value.workshopId(), value.code(), value.name(), value.centerType(),
                    value.concurrentCapacity(), value.capacityUomCode(), value.status(), value.remark(),
                    value.rowVersion() + 1);
        });
    }

    public List<Resource> listResources(ResourceAccessScope scope, String workshopId, String workCenterId)
    {
        if (workshopId != null && !workshopId.isBlank())
        {
            requireWorkshop(scope, workshopId);
        }
        return repository.listResources(scope, blankToNull(workshopId), blankToNull(workCenterId));
    }

    public Resource saveResource(ResourceAccessScope scope, ApsAuditActor actor, Resource input)
    {
        requireWorkshop(scope, input.workshopId());
        if (input.workCenterId() != null && !input.workCenterId().isBlank())
        {
            WorkCenter center = repository.findWorkCenter(scope, input.workCenterId()).orElseThrow(this::notFound);
            if (!center.workshopId().equals(input.workshopId()))
            {
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "资源和工作中心必须属于同一车间");
            }
        }
        requireText(input.code(), "资源编码");
        requireText(input.name(), "资源名称");
        positive(input.capacityValue(), "资源容量");
        Resource value = new Resource(idOrNew(input.id()), input.workshopId(), blankToNull(input.workCenterId()),
                normalizeCode(input.code()), input.name().trim(), Objects.requireNonNull(input.type(), "资源类型不能为空"),
                blankToNull(input.ruoyiUserId()), blankToNull(input.teamName()), input.capacityValue(),
                defaultText(input.capacityUomCode(), "COUNT").toUpperCase(Locale.ROOT),
                enumText(input.status(), "ACTIVE", Set.of("ACTIVE", "INACTIVE", "MAINTENANCE")),
                blankToNull(input.remark()), input.rowVersion());
        return transactions.required(() -> {
            if (input.id() == null || input.id().isBlank())
            {
                repository.insertResource(value, actor.userId());
                return value;
            }
            Resource current = repository.findResource(scope, input.id()).orElseThrow(this::notFound);
            if (!current.workshopId().equals(input.workshopId()))
            {
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "不能跨车间移动资源");
            }
            requireUpdated(repository.updateResource(value, actor.userId()));
            return new Resource(value.id(), value.workshopId(), value.workCenterId(), value.code(), value.name(),
                    value.type(), value.ruoyiUserId(), value.teamName(), value.capacityValue(), value.capacityUomCode(),
                    value.status(), value.remark(), value.rowVersion() + 1);
        });
    }

    public List<Skill> listSkills(ResourceAccessScope scope, String resourceId)
    {
        requireResource(scope, resourceId);
        return repository.listSkills(scope, resourceId);
    }

    public Skill saveSkill(ResourceAccessScope scope, ApsAuditActor actor, Skill input)
    {
        requireResource(scope, input.resourceId());
        requireText(input.code(), "技能编码");
        requireText(input.name(), "技能名称");
        if (input.level() < 1 || input.level() > 10)
        {
            throw invalid("技能等级必须在 1 到 10 之间");
        }
        if (input.validFrom() != null && input.validTo() != null && !input.validTo().isAfter(input.validFrom()))
        {
            throw invalid("技能有效期结束必须晚于开始");
        }
        Skill value = new Skill(idOrNew(input.id()), input.resourceId(), normalizeCode(input.code()), input.name().trim(),
                input.level(), input.validFrom(), input.validTo(), blankToNull(input.certificateRef()),
                enumText(input.status(), "ACTIVE", Set.of("ACTIVE", "INACTIVE", "EXPIRED")), input.rowVersion());
        return transactions.required(() -> {
            if (input.id() == null || input.id().isBlank())
            {
                repository.insertSkill(value, actor.userId());
                return value;
            }
            requireUpdated(repository.updateSkill(value, actor.userId()));
            return new Skill(value.id(), value.resourceId(), value.code(), value.name(), value.level(),
                    value.validFrom(), value.validTo(), value.certificateRef(), value.status(), value.rowVersion() + 1);
        });
    }

    public List<Availability> listAvailability(ResourceAccessScope scope, String resourceId)
    {
        requireResource(scope, resourceId);
        return repository.listAvailability(scope, resourceId);
    }

    public Availability saveAvailability(ResourceAccessScope scope, ApsAuditActor actor, Availability input)
    {
        requireResource(scope, input.resourceId());
        try
        {
            new UtcTimeWindow(input.startAt(), input.endAt());
        }
        catch (RuntimeException exception)
        {
            throw invalid("资源时间窗必须是有效 UTC 半开区间");
        }
        positive(input.capacityRatio(), "容量比例");
        if (input.capacityRatio().compareTo(BigDecimal.ONE) > 0)
        {
            throw invalid("容量比例不能大于 1");
        }
        Availability value = new Availability(idOrNew(input.id()), input.resourceId(),
                Objects.requireNonNull(input.type(), "时间窗类型不能为空"), input.startAt(), input.endAt(),
                input.capacityRatio(), defaultText(input.sourceType(), "MANUAL").toUpperCase(Locale.ROOT),
                blankToNull(input.sourceRef()), blankToNull(input.reason()), input.rowVersion());
        return transactions.required(() -> {
            if (input.id() == null || input.id().isBlank())
            {
                repository.insertAvailability(value, actor.userId());
                return value;
            }
            requireUpdated(repository.updateAvailability(value, actor.userId()));
            return new Availability(value.id(), value.resourceId(), value.type(), value.startAt(), value.endAt(),
                    value.capacityRatio(), value.sourceType(), value.sourceRef(), value.reason(), value.rowVersion() + 1);
        });
    }

    public List<Availability> importAvailability(ResourceAccessScope scope, ApsAuditActor actor,
            List<Availability> inputs)
    {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 10_000)
        {
            throw invalid("单次时间窗导入数量必须在 1 到 10000 之间");
        }
        return transactions.required(() -> inputs.stream()
                .map(input -> saveAvailability(scope, actor, input)).toList());
    }

    public List<NetWindow> netAvailability(ResourceAccessScope scope, String resourceId, Instant from, Instant to)
    {
        requireResource(scope, resourceId);
        return calendar.compile(from, to, toDomainWindows(repository.listAvailability(scope, resourceId)));
    }

    public List<Candidate> candidates(ResourceAccessScope scope, String workshopId, String skillCode, int level,
            Instant start, Instant end)
    {
        requireWorkshop(scope, workshopId);
        if (level < 1 || level > 10)
        {
            throw invalid("最低技能等级必须在 1 到 10 之间");
        }
        new UtcTimeWindow(start, end);
        List<Candidate> result = new ArrayList<>();
        for (Resource resource : repository.listResources(scope, workshopId, null))
        {
            List<ResourceSkill> skills = repository.listSkills(scope, resource.id()).stream().map(this::toDomainSkill).toList();
            List<AvailabilityWindow> windows = toDomainWindows(repository.listAvailability(scope, resource.id()));
            if (calendar.isQualified(resource.resourceStatus(), skills, windows, normalizeCode(skillCode), level, start, end))
            {
                result.add(new Candidate(resource.id(), resource.code(), resource.name()));
            }
        }
        return List.copyOf(result);
    }

    public List<ReadinessIssue> readiness(ResourceAccessScope scope, String workshopId, Instant at)
    {
        requireWorkshop(scope, workshopId);
        List<ReadinessIssue> issues = new ArrayList<>();
        List<WorkCenter> centers = repository.listWorkCenters(scope, workshopId);
        if (centers.isEmpty())
        {
            issues.add(new ReadinessIssue("WORKSHOP_WITHOUT_CENTER", "WORKSHOP", workshopId, "车间没有工作中心"));
        }
        for (Resource resource : repository.listResources(scope, workshopId, null))
        {
            if (resource.workCenterId() == null)
            {
                issues.add(new ReadinessIssue("RESOURCE_WITHOUT_CENTER", "RESOURCE", resource.id(), "资源没有工作中心"));
            }
            if (!"ACTIVE".equals(resource.status()) && repository.hasPlanOrExecutionReference(scope, resource.id()))
            {
                issues.add(new ReadinessIssue("INACTIVE_RESOURCE_REFERENCED", "RESOURCE", resource.id(),
                        "停用或维护资源仍被计划、锁定或执行记录引用"));
            }
            List<Skill> skills = repository.listSkills(scope, resource.id());
            for (Skill skill : skills)
            {
                if (skill.validTo() != null && !at.isBefore(skill.validTo()))
                {
                    issues.add(new ReadinessIssue("SKILL_EXPIRED", "SKILL", skill.id(), "技能已过有效期"));
                }
            }
            List<Availability> windows = repository.listAvailability(scope, resource.id());
            if (windows.stream().noneMatch(window -> window.type().productive()))
            {
                issues.add(new ReadinessIssue("RESOURCE_WITHOUT_AVAILABLE_WINDOW", "RESOURCE", resource.id(),
                        "资源没有可用或加班时间窗"));
            }
            detectConflicts(windows, issues);
        }
        return List.copyOf(issues);
    }

    private void detectConflicts(List<Availability> windows, List<ReadinessIssue> issues)
    {
        Set<String> emitted = new HashSet<>();
        for (int left = 0; left < windows.size(); left++)
        {
            for (int right = left + 1; right < windows.size(); right++)
            {
                Availability first = windows.get(left);
                Availability second = windows.get(right);
                boolean overlap = first.startAt().isBefore(second.endAt()) && second.startAt().isBefore(first.endAt());
                if (overlap && first.type().productive() != second.type().productive()
                        && emitted.add(first.resourceId()))
                {
                    issues.add(new ReadinessIssue("AVAILABILITY_CONFLICT", "RESOURCE", first.resourceId(),
                            "可用窗与不可用窗重叠，将按不可用窗扣减"));
                }
            }
        }
    }

    private List<AvailabilityWindow> toDomainWindows(List<Availability> values)
    {
        return values.stream().map(value -> new AvailabilityWindow(value.id(), value.resourceId(), value.type(),
                new UtcTimeWindow(value.startAt(), value.endAt()), value.capacityRatio(), value.sourceType(),
                value.sourceRef(), value.reason())).toList();
    }

    private ResourceSkill toDomainSkill(Skill value)
    {
        return new ResourceSkill(value.id(), value.resourceId(), value.code(), value.name(), value.level(),
                value.validFrom(), value.validTo(), value.certificateRef(), value.status());
    }

    private Workshop requireWorkshop(ResourceAccessScope scope, String id)
    {
        return repository.findWorkshop(scope, id).orElseThrow(this::forbidden);
    }

    private Resource requireResource(ResourceAccessScope scope, String id)
    {
        return repository.findResource(scope, id).orElseThrow(this::forbidden);
    }

    private ApsBusinessException forbidden()
    {
        return new ApsBusinessException(ApsErrorCode.FORBIDDEN, "无权访问该车间或资源");
    }

    private ApsBusinessException notFound()
    {
        return new ApsBusinessException(ApsErrorCode.NOT_FOUND, "对象不存在");
    }

    private ApsBusinessException invalid(String message)
    {
        return new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
    }

    private void requireUpdated(int count)
    {
        if (count == 0)
        {
            throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "数据已被其他请求更新，请刷新后重试");
        }
    }

    private void requireText(String value, String name)
    {
        if (value == null || value.isBlank())
        {
            throw invalid(name + "不能为空");
        }
    }

    private void positive(BigDecimal value, String name)
    {
        if (value == null || value.signum() <= 0)
        {
            throw invalid(name + "必须大于 0");
        }
    }

    private String idOrNew(String id)
    {
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }

    private String normalizeCode(String value)
    {
        requireText(value, "编码");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String lifecycle(String value)
    {
        return enumText(value, "ACTIVE", Set.of("ACTIVE", "INACTIVE"));
    }

    private String enumText(String value, String defaultValue, Set<String> allowed)
    {
        String normalized = defaultText(value, defaultValue).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized))
        {
            throw invalid("状态或类型值无效: " + normalized);
        }
        return normalized;
    }

    private String defaultText(String value, String defaultValue)
    {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
