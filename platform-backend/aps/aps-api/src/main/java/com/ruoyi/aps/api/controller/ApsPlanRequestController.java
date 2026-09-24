package com.ruoyi.aps.api.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.ruoyi.aps.api.dto.ApsUtcInput;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.application.planning.PlanRequestService;
import com.ruoyi.aps.application.planning.SolverInputCompiler;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** IMP-07 计划请求的创建、固定共享批配置、状态恢复与取消入口；不提供发布动作。 */
@RestController
@RequestMapping("/api/aps/v1/plan-requests")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
public class ApsPlanRequestController
{
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private final PlanRequestService service;
    private final ApsAuditActorProvider actors;
    private final ApsPlanProgressStream progress;

    public ApsPlanRequestController(PlanRequestService service, ApsAuditActorProvider actors,
            ApsPlanProgressStream progress)
    {
        this.service = service;
        this.actors = actors;
        this.progress = progress;
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('aps:planning:solve')")
    public ResponseEntity<PlanRequestStatusResponse> create(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String requestId,
            @Valid @RequestBody PlanRequestInput input)
    {
        SolverInputCompiler.CompileRequest request = input.toCompileRequest(requestId, planVersionId(requestId));
        PlanRequestService.CreatedPlan created = service.create(scope(), request, actor().username());
        PlanRepository.PlanRequestSnapshot snapshot = service.status(scope(), requestId);
        return ResponseEntity.status(created.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(response(snapshot));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("@ss.hasPermi('aps:planning:view')")
    public PlanRequestStatusResponse status(@PathVariable @Pattern(regexp = UUID_PATTERN) String requestId)
    {
        return response(service.status(scope(), requestId));
    }

    @GetMapping(path = "/{requestId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasPermi('aps:planning:view')")
    public SseEmitter events(@PathVariable @Pattern(regexp = UUID_PATTERN) String requestId)
    {
        return progress.stream(scope(), requestId);
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("@ss.hasPermi('aps:planning:cancel')")
    public PlanRequestStatusResponse cancel(@PathVariable @Pattern(regexp = UUID_PATTERN) String requestId)
    {
        return response(service.cancel(scope(), requestId, actor().username()));
    }

    private PlanRequestStatusResponse response(PlanRepository.PlanRequestSnapshot snapshot)
    {
        PlanRepository.PlanRecord plan = snapshot.plan();
        SolverInput input = snapshot.input();
        BaseVersionSummary base = input.baseVersion() == null ? null
                : new BaseVersionSummary(input.baseVersion().planVersionId(), input.baseVersion().inputHash());
        return new PlanRequestStatusResponse("1.0", "PLAN_REQUEST_STATUS", plan.requestId(), plan.id(),
                plan.definitionRevision(), plan.executionRevision(), plan.inputHash(),
                new HorizonSummary(input.horizon().startAt(), input.horizon().detailEndAt(), input.horizon().endAt(),
                        input.horizon().planningAnchorAt(), input.horizon().timeUnitSeconds()),
                base, plan.status(), snapshot.solverStatus() == null ? null : snapshot.solverStatus().name(),
                snapshot.resultKind() == null ? null : snapshot.resultKind().name(), snapshot.reasonCodes(),
                plan.updatedAt());
    }

    private ResourceAccessScope scope()
    {
        ApsAuditActor actor = actor();
        return new ResourceAccessScope(actor.userId(), SecurityUtils.isAdmin() || SecurityUtils.hasPermi("aps:scope:manage"));
    }

    private ApsAuditActor actor() { return actors.currentActor(); }

    private String planVersionId(String requestId)
    {
        return UUID.nameUUIDFromBytes(("aps-plan\u0000" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record PlanRequestInput(
            @NotBlank @Size(max = 64) String siteCode,
            @NotEmpty @Size(max = 100) List<@Pattern(regexp = UUID_PATTERN) String> workshopIds,
            @NotEmpty @Size(max = 1000) List<@Pattern(regexp = UUID_PATTERN) String> orderIds,
            @NotBlank String capturedAt,
            @PositiveOrZero long definitionRevision,
            @PositiveOrZero long executionRevision,
            @NotNull @Valid HorizonInput horizon,
            @NotNull @Valid ParametersInput parameters,
            @Size(max = 200) List<@Valid SharedBatchInput> sharedBatches,
            @Valid BaseVersionInput baseVersion)
    {
        SolverInputCompiler.CompileRequest toCompileRequest(String requestId, String planVersionId)
        {
            return new SolverInputCompiler.CompileRequest(requestId, planVersionId, siteCode, workshopIds, orderIds,
                    ApsUtcInput.parse(capturedAt), definitionRevision, executionRevision,
                    ApsUtcInput.parse(horizon.startAt), ApsUtcInput.parse(horizon.detailEndAt),
                    ApsUtcInput.parse(horizon.endAt), ApsUtcInput.parse(horizon.planningAnchorAt),
                    horizon.timeUnitSeconds, horizon.displayTimeZone, "aps-cpsat-v1", parameters.maxSolveSeconds,
                    parameters.randomSeed, parameters.solverSearchThreads, parameters.absoluteGapLimit,
                    parameters.relativeGapLimit, sharedBatches == null ? List.of() : sharedBatches.stream()
                            .map(SharedBatchInput::toRequest).toList(), baseVersion == null ? null
                                    : new SolverInputCompiler.BaseVersionRequest(baseVersion.planVersionId,
                                            baseVersion.inputHash, ApsUtcInput.parse(baseVersion.freezeEndAt)));
        }
    }

    public record BaseVersionInput(@NotBlank @Pattern(regexp = UUID_PATTERN) String planVersionId,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String inputHash,
            @NotBlank String freezeEndAt) { }

    public record SharedBatchInput(@NotBlank @Size(max = 128) String compatibilityKey,
            @NotEmpty @Size(max = 100) List<@Valid SharedBatchMemberInput> members)
    {
        SolverInputCompiler.SharedBatchRequest toRequest()
        {
            return new SolverInputCompiler.SharedBatchRequest(compatibilityKey,
                    members.stream().map(value -> new SolverInputCompiler.SharedBatchMemberRequest(
                            value.taskId(), value.quantity())).toList());
        }
    }

    public record SharedBatchMemberInput(@NotBlank @Pattern(regexp = UUID_PATTERN) String taskId,
            @NotNull @Positive BigDecimal quantity) { }

    public record HorizonInput(@NotBlank String startAt, @NotBlank String detailEndAt, @NotBlank String endAt,
            @NotBlank String planningAnchorAt, @Min(1) @Max(60) int timeUnitSeconds,
            @NotBlank @Size(max = 64) String displayTimeZone) { }

    public record ParametersInput(@Min(1) @Max(3600) int maxSolveSeconds, int randomSeed,
            @Min(1) @Max(32) int solverSearchThreads,
            @DecimalMin("0.0") @DecimalMax("1000000000.0") double absoluteGapLimit,
            @DecimalMin("0.0") @DecimalMax("1.0") double relativeGapLimit) { }

    public record PlanRequestStatusResponse(String schemaVersion, String contractType, String requestId,
            String planVersionId, long definitionRevision, long executionRevision, String inputHash,
            HorizonSummary horizon, BaseVersionSummary baseVersion, String planStatus, String solverStatus,
            String resultKind, List<String> reasonCodes, Instant updatedAt) { }

    public record HorizonSummary(Instant startAt, Instant detailEndAt, Instant endAt, Instant planningAnchorAt,
            int timeUnitSeconds) { }
    public record BaseVersionSummary(String planVersionId, String inputHash) { }
}
