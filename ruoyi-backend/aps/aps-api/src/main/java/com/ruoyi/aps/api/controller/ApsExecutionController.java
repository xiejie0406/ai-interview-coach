package com.ruoyi.aps.api.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.api.dto.ApsUtcInput;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ActualOccupancy;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ExecutionRun;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OccupancyActivityType;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OutputLot;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ProductionReport;
import com.ruoyi.aps.application.execution.ExecutionLifecycleService;
import com.ruoyi.aps.application.execution.ProductionReportingService;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.domain.execution.ExecutionAction;
import com.ruoyi.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** IMP09-B 执行 run 与真实占用 HTTP 适配层。 */
@RestController
@RequestMapping("/api/aps/v1/execution-runs")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
public class ApsExecutionController
{
    private static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private final ExecutionLifecycleService service;
    private final ProductionReportingService reporting;
    private final ApsAuditActorProvider actors;

    public ApsExecutionController(ExecutionLifecycleService service, ProductionReportingService reporting,
            ApsAuditActorProvider actors)
    {
        this.service = service;
        this.reporting = reporting;
        this.actors = actors;
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('aps:execution:start')")
    public ResponseEntity<ExecutionRunDetailResponse> create(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID) String requestId,
            @Valid @RequestBody CreateExecutionRunRequest request)
    {
        ApsAuditActor actor = actors.currentActor();
        var result = service.create(scope(actor), new ExecutionLifecycleService.CreateRun(requestId,
                request.planVersionId(), request.planJobId(), request.assignedQty(), request.uomCode()),
                actor.username());
        return ResponseEntity.status(result.reused() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response(service.detail(scope(actor), result.run().id())));
    }

    @GetMapping("/{executionRunId}")
    @PreAuthorize("@ss.hasPermi('aps:execution:view')")
    public ExecutionRunDetailResponse detail(@PathVariable @Pattern(regexp = UUID) String executionRunId)
    {
        ApsAuditActor actor = actors.currentActor();
        return response(service.detail(scope(actor), executionRunId));
    }

    @PostMapping("/{executionRunId}/transitions")
    @PreAuthorize("((#request.action == 'START' or #request.action == 'RESUME') and @ss.hasPermi('aps:execution:start')) or ((#request.action == 'PAUSE' or #request.action == 'CANCEL') and @ss.hasPermi('aps:execution:pause'))")
    public ExecutionRunDetailResponse transition(@PathVariable @Pattern(regexp = UUID) String executionRunId,
            @Valid @RequestBody ExecutionTransitionRequest request)
    {
        ApsAuditActor actor = actors.currentActor();
        service.transition(scope(actor), new ExecutionLifecycleService.Transition(executionRunId,
                ExecutionAction.valueOf(request.action()), request.expectedRowVersion(),
                ApsUtcInput.parse(request.occurredAt()), request.reason()), actor.username());
        return response(service.detail(scope(actor), executionRunId));
    }

    @PostMapping("/{executionRunId}/resource-changes")
    @PreAuthorize("@ss.hasPermi('aps:execution:start')")
    public ExecutionRunDetailResponse changeResource(
            @PathVariable @Pattern(regexp = UUID) String executionRunId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID) String requestId,
            @Valid @RequestBody ExecutionResourceChangeRequest request)
    {
        ApsAuditActor actor = actors.currentActor();
        service.changeResource(scope(actor), new ExecutionLifecycleService.ResourceChange(requestId,
                executionRunId, request.expectedRowVersion(), request.replacedResourceId(), request.resourceId(),
                request.planSegmentId(), OccupancyActivityType.valueOf(request.activityType()),
                ApsUtcInput.parse(request.occurredAt()), request.reason()), actor.username());
        return response(service.detail(scope(actor), executionRunId));
    }

    @PostMapping("/{executionRunId}/phase-advances")
    @PreAuthorize("@ss.hasPermi('aps:execution:start')")
    public ExecutionRunDetailResponse advancePhase(
            @PathVariable @Pattern(regexp = UUID) String executionRunId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID) String requestId,
            @Valid @RequestBody ExecutionPhaseAdvanceRequest request)
    {
        ApsAuditActor actor = actors.currentActor();
        service.advancePhase(scope(actor), new ExecutionLifecycleService.PhaseAdvance(requestId,
                executionRunId, request.expectedRowVersion(), ApsUtcInput.parse(request.occurredAt()),
                request.reason()), actor.username());
        return response(service.detail(scope(actor), executionRunId));
    }

    private ExecutionRunDetailResponse response(ExecutionLifecycleService.ExecutionDetail detail)
    {
        return new ExecutionRunDetailResponse("1.0", "EXECUTION_RUN_DETAIL", detail.run(),
                detail.occupancies(), reporting.listReports(detail.run().id()),
                reporting.listOutputLots(detail.run().id()), detail.executionRevision());
    }

    private ResourceAccessScope scope(ApsAuditActor actor)
    {
        return new ResourceAccessScope(actor.userId(),
                SecurityUtils.isAdmin() || SecurityUtils.hasPermi("aps:scope:manage"));
    }

    public record CreateExecutionRunRequest(@NotBlank @Pattern(regexp = UUID) String planVersionId,
            @NotBlank @Pattern(regexp = UUID) String planJobId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal assignedQty,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,15}$") String uomCode) { }

    public record ExecutionTransitionRequest(@NotBlank @Pattern(regexp = "START|PAUSE|RESUME|CANCEL") String action,
            @Min(0) long expectedRowVersion, @NotBlank String occurredAt,
            @Size(max = 500) String reason) { }

    public record ExecutionResourceChangeRequest(@Min(0) long expectedRowVersion,
            @NotBlank @Pattern(regexp = UUID) String replacedResourceId,
            @NotBlank @Pattern(regexp = UUID) String resourceId,
            @Pattern(regexp = UUID) String planSegmentId,
            @NotBlank @Pattern(regexp = "SETUP|RUN|UNLOAD|WAIT_HOLD|PAUSE_HOLD|TRANSPORT") String activityType,
            @NotBlank String occurredAt, @Size(max = 500) String reason) { }

    public record ExecutionPhaseAdvanceRequest(@Min(0) long expectedRowVersion,
            @NotBlank String occurredAt, @Size(max = 500) String reason) { }

    public record ExecutionRunDetailResponse(String schemaVersion, String contractType, ExecutionRun run,
            List<ActualOccupancy> occupancies, List<ProductionReport> reports, List<OutputLot> outputLots,
            long executionRevision) { }
}
