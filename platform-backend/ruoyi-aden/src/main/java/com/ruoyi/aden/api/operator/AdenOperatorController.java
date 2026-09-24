package com.ruoyi.aden.api.operator;

import com.ruoyi.aden.api.common.AdenCorrelationIdFilter;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.runner.AdenRunnerAdministrationService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.security.AdenOperatorPrincipalProvider;
import com.ruoyi.aden.application.task.AdenOperatorTaskCommandAdapter;
import com.ruoyi.aden.application.task.AdenSyntheticTaskInput;
import com.ruoyi.aden.application.task.AdenTaskCommandService;
import com.ruoyi.aden.application.task.AdenTaskEtag;
import com.ruoyi.aden.application.task.AdenTaskTransactionService;
import com.ruoyi.aden.contract.OperatorTaskCommand;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** contracts/aden/openapi/operator-v1.openapi.json 的 Operator REST 适配器。 */
@Validated
@RestController
@RequestMapping("/api/v1/aden/workspaces/{workspaceId}")
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true")
public class AdenOperatorController {
    private final AdenOperatorPrincipalProvider principals;
    private final AdenOperatorQueryService queries;
    private final AdenTaskCommandService tasks;
    private final AdenRunnerAdministrationService runners;

    public AdenOperatorController(AdenOperatorPrincipalProvider principals,
                                  AdenOperatorQueryService queries,
                                  AdenTaskCommandService tasks,
                                  AdenRunnerAdministrationService runners) {
        this.principals = principals;
        this.queries = queries;
        this.tasks = tasks;
        this.runners = runners;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<AdenOperatorQueryService.Bootstrap> bootstrap(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int taskLimit,
            @RequestParam(required = false) @Size(min = 16, max = 768) String taskCursor,
            @RequestParam(required = false) String capability,
            HttpServletRequest request) {
        var body = queries.bootstrap(principals.current(), workspace(workspaceId), capability,
                taskCursor, taskLimit, correlation(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/capabilities")
    @PreAuthorize("@ss.hasPermi('aden:capability:list')")
    public AdenOperatorQueryService.CapabilityList capabilities(@PathVariable String workspaceId,
                                                                 HttpServletRequest request) {
        return queries.listCapabilities(principals.current(), workspace(workspaceId), correlation(request));
    }

    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPermi('aden:task:list')")
    public AdenOperatorQueryService.TaskPage tasks(@PathVariable String workspaceId,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
                                                   @RequestParam(required = false) String capability,
                                                   HttpServletRequest request) {
        return queries.listTasks(principals.current(), workspace(workspaceId), capability,
                cursor, limit, correlation(request));
    }

    @PostMapping("/tasks")
    @PreAuthorize("@ss.hasPermi('aden:task:create')")
    public ResponseEntity<AdenOperatorQueryService.TaskSnapshot> createTask(
            @PathVariable String workspaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest body,
            HttpServletRequest request) {
        AdenOperatorPrincipal principal = principals.current();
        AdenWorkspaceId workspace = workspace(workspaceId);
        String correlation = correlation(request);
        var result = tasks.createTask(new AdenTaskTransactionService.CreateTask(
                principal, workspace, new AdenIdempotencyKey(idempotencyKey), body.title(),
                new AdenSyntheticTaskInput(body.input().fixtureId(), body.input().instruction(),
                        body.input().expectedOutcome()), new AdenCorrelationId(correlation)));
        var snapshot = queries.getTaskAfterWrite(principal, workspace, result.taskId().value(),
                AdenTaskTransactionService.CREATE_PERMISSION, correlation);
        return ResponseEntity.created(URI.create("/api/v1/aden/workspaces/" + workspaceId
                        + "/tasks/" + snapshot.taskId()))
                .eTag(AdenTaskEtag.encode(snapshot.taskId(), snapshot.version())).body(snapshot);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("@ss.hasPermi('aden:task:query')")
    public ResponseEntity<AdenOperatorQueryService.TaskSnapshot> task(
            @PathVariable String workspaceId, @PathVariable String taskId,
            HttpServletRequest request) {
        var snapshot = queries.getTask(principals.current(), workspace(workspaceId), uuid(taskId, "taskId"),
                correlation(request));
        return ResponseEntity.ok().eTag(AdenTaskEtag.encode(snapshot.taskId(), snapshot.version())).body(snapshot);
    }

    @PostMapping("/tasks/{taskId}/commands")
    public ResponseEntity<AdenOperatorQueryService.TaskSnapshot> command(
            @PathVariable String workspaceId, @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody TaskCommandRequest body,
            HttpServletRequest request) {
        AdenOperatorPrincipal principal = principals.current();
        AdenWorkspaceId workspace = workspace(workspaceId);
        AdenTaskId id = new AdenTaskId(uuid(taskId, "taskId"));
        String correlation = correlation(request);
        var mapped = AdenOperatorTaskCommandAdapter.map(body.command());
        if (body.command() == OperatorTaskCommand.REQUEST_CANCEL && body.reasonCode() == null) {
            throw new IllegalArgumentException("REQUEST_CANCEL 必须提供 reasonCode");
        }
        if (body.command() == OperatorTaskCommand.SUBMIT_FOR_VALIDATION && body.reasonCode() != null) {
            throw new IllegalArgumentException("SUBMIT_FOR_VALIDATION 不允许提供 reasonCode");
        }
        var version = AdenTaskEtag.decode(ifMatch, id);
        if (body.command() == OperatorTaskCommand.SUBMIT_FOR_VALIDATION) {
            tasks.submitForValidation(new AdenTaskTransactionService.SubmitForValidation(
                    principal, workspace, id, version, new AdenIdempotencyKey(idempotencyKey),
                    new AdenCorrelationId(correlation)));
        } else {
            tasks.requestCancel(new AdenTaskTransactionService.RequestCancel(
                    principal, workspace, id, version, new AdenIdempotencyKey(idempotencyKey),
                    new AdenCorrelationId(correlation)));
        }
        var snapshot = queries.getTaskAfterWrite(principal, workspace, id.value(),
                mapped.permission(), correlation);
        return ResponseEntity.ok().eTag(AdenTaskEtag.encode(snapshot.taskId(), snapshot.version())).body(snapshot);
    }

    @GetMapping("/runners")
    @PreAuthorize("@ss.hasPermi('aden:runner:list')")
    public AdenOperatorQueryService.RunnerList runners(@PathVariable String workspaceId,
                                                        HttpServletRequest request) {
        return queries.listRunners(principals.current(), workspace(workspaceId), correlation(request));
    }

    @PostMapping("/runners:enroll")
    @PreAuthorize("@ss.hasPermi('aden:runner:enroll')")
    public ResponseEntity<RunnerEnrollmentResponse> enroll(
            @PathVariable String workspaceId, @Valid @RequestBody EnrollRunnerRequest body,
            HttpServletRequest request) {
        var result = runners.enroll(new AdenRunnerAdministrationService.Enroll(
                principals.current(), workspace(workspaceId), body.displayName(), body.capabilities(),
                new AdenCorrelationId(correlation(request))));
        var response = new RunnerEnrollmentResponse(result.runnerId().value(), result.credentialId().value(),
                result.credentialToken(), Long.toString(result.credentialEpoch()),
                result.expiresAt(), result.issuedAt());
        return ResponseEntity.created(URI.create("/api/v1/aden/workspaces/" + workspaceId
                        + "/runners/" + response.runnerId()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/runners/{runnerId}:revoke")
    @PreAuthorize("@ss.hasPermi('aden:runner:revoke')")
    public ResponseEntity<Void> revoke(@PathVariable String workspaceId, @PathVariable String runnerId,
                                       HttpServletRequest request) {
        runners.revoke(new AdenRunnerAdministrationService.Revoke(principals.current(), workspace(workspaceId),
                new AdenRunnerId(uuid(runnerId, "runnerId")), new AdenCorrelationId(correlation(request))));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-events")
    @PreAuthorize("@ss.hasPermi('aden:audit:list')")
    public AdenOperatorQueryService.AuditPage audit(@PathVariable String workspaceId,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
                                                    HttpServletRequest request) {
        return queries.listAudit(principals.current(), workspace(workspaceId), cursor, limit, correlation(request));
    }

    private static AdenWorkspaceId workspace(String value) { return new AdenWorkspaceId(uuid(value, "workspaceId")); }
    private static String correlation(HttpServletRequest request) { return AdenCorrelationIdFilter.correlationId(request); }
    private static String uuid(String value, String field) {
        try { String parsed=UUID.fromString(value).toString(); if(!parsed.equals(value))throw new IllegalArgumentException(); return parsed; }
        catch(RuntimeException exception){throw new IllegalArgumentException(field+" 必须是 canonical UUID");}
    }

    public record SyntheticInput(@NotBlank @Pattern(regexp = "^fixture:[a-z0-9][a-z0-9._-]{2,63}$") String fixtureId,
                                 @NotBlank @Size(max = 1000) String instruction,
                                 AdenSyntheticTaskInput.ExpectedOutcome expectedOutcome) { }
    public record CreateTaskRequest(@NotNull String taskType, @NotNull AdenCapabilityCode capabilityCode,
                                    @NotBlank @Size(max = 120) String title,
                                    @NotNull @Valid SyntheticInput input) {
        public CreateTaskRequest {
            if (!"SYNTHETIC_CORE".equals(taskType) || capabilityCode != AdenCapabilityCode.CORE) {
                throw new IllegalArgumentException("当前只允许 SYNTHETIC_CORE / CORE");
            }
        }
    }
    public record TaskCommandRequest(@NotNull OperatorTaskCommand command,
                                     @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String reasonCode) { }
    public record EnrollRunnerRequest(@NotBlank @Size(max = 80) String displayName,
                                      @NotEmpty @Size(max = 4) Set<@NotNull AdenCapabilityCode> capabilities) { }
    public record RunnerEnrollmentResponse(String runnerId, String credentialId, String credentialToken,
                                           String credentialEpoch, java.time.Instant expiresAt,
                                           java.time.Instant issuedAt) { }
}
