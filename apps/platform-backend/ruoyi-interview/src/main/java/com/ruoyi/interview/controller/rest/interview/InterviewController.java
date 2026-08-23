package com.ruoyi.interview.controller.rest.interview;

import com.ruoyi.interview.controller.rest.common.HttpVersionPreconditions;
import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.application.interview.ConfirmInterviewPlan;
import com.ruoyi.interview.application.interview.CreateInterviewPlan;
import com.ruoyi.interview.application.interview.CreateInterviewSession;
import com.ruoyi.interview.application.interview.InterviewPlanView;
import com.ruoyi.interview.application.interview.InterviewSessionSnapshot;
import com.ruoyi.interview.application.interview.InterviewTargetLevel;
import com.ruoyi.interview.application.interview.InterviewTargetRole;
import com.ruoyi.interview.application.interview.RecoverInterview;
import com.ruoyi.interview.application.interview.ProgressInterview;
import com.ruoyi.interview.application.interview.StartInterview;
import com.ruoyi.interview.application.interview.SubmitInterviewAnswer;
import com.ruoyi.interview.application.interview.ApplySessionCommand;
import com.ruoyi.interview.domain.interview.InterviewMode;
import com.ruoyi.interview.domain.interview.InterviewAnswerSource;
import com.ruoyi.interview.domain.interview.SessionCommandType;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/** Vue 前台面试配置、确认、创建会话和刷新恢复的 REST 入站层。 */
@Validated
@RestController
@PreAuthorize("@ss.hasPermi('interview:session:start')")
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class InterviewController {
    private final CreateInterviewPlan createPlan;
    private final ConfirmInterviewPlan confirmPlan;
    private final CreateInterviewSession createSession;
    private final RecoverInterview recover;
    private final StartInterview start;
    private final SubmitInterviewAnswer submitAnswer;
    private final ApplySessionCommand applyCommand;
    private final ProgressInterview progress;
    private final RequestContextFactory contexts;

    public InterviewController(CreateInterviewPlan createPlan, ConfirmInterviewPlan confirmPlan,
                               CreateInterviewSession createSession, RecoverInterview recover,
                               StartInterview start, SubmitInterviewAnswer submitAnswer,
                               ApplySessionCommand applyCommand, ProgressInterview progress,
                               RequestContextFactory contexts) {
        this.createPlan = createPlan;
        this.confirmPlan = confirmPlan;
        this.createSession = createSession;
        this.recover = recover;
        this.start = start;
        this.submitAnswer = submitAnswer;
        this.applyCommand = applyCommand;
        this.progress = progress;
        this.contexts = contexts;
    }

    @PostMapping(path = "/interview-plans", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest body,
                                               HttpServletRequest request) {
        InterviewPlanView result = createPlan.handle(new CreateInterviewPlan.Command(
                InterviewTargetRole.valueOf(body.targetRole()), InterviewTargetLevel.valueOf(body.targetLevel()),
                body.topics(), body.durationMinutes(), mode(body.mode()), contexts.operation(request)));
        return ResponseEntity.status(201).eTag(HttpVersionPreconditions.etag(result.version()))
                .body(PlanResponse.from(result));
    }

    @PostMapping(path = "/interview-plans/{planId}/commands/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanResponse> confirm(@PathVariable UUID planId,
                                                @RequestHeader("If-Match")
                                                @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
                                                @Valid @RequestBody ConfirmPlanRequest body,
                                                HttpServletRequest request) {
        InterviewPlanView result = confirmPlan.handle(new ConfirmInterviewPlan.Command(
                ResourceId.of(planId), body.acknowledgedEstimateVersion(),
                HttpVersionPreconditions.requireIfMatch(ifMatch), contexts.operation(request)));
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(result.version()))
                .body(PlanResponse.from(result));
    }

    @PostMapping(path = "/interviews", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest body,
                                                         HttpServletRequest request) {
        InterviewSessionSnapshot result = createSession.handle(new CreateInterviewSession.Command(
                ResourceId.of(body.confirmedPlanId()), body.planVersionNo(), contexts.operation(request)));
        return ResponseEntity.status(201).eTag(HttpVersionPreconditions.etag(result.version()))
                .body(SessionResponse.from(result));
    }

    @GetMapping("/interviews/{sessionId}")
    public ResponseEntity<SessionResponse> recover(@PathVariable UUID sessionId, HttpServletRequest request) {
        InterviewSessionSnapshot result = recover.handle(new RecoverInterview.Query(
                ResourceId.of(sessionId), contexts.query(request)));
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(result.version()))
                .body(SessionResponse.from(result));
    }

    @PostMapping("/interviews/{sessionId}/commands/start")
    public ResponseEntity<SessionResponse> start(@PathVariable UUID sessionId,
                                                 @RequestHeader("If-Match")
                                                 @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
                                                 HttpServletRequest request) {
        var started = start.handle(new StartInterview.Command(ResourceId.of(sessionId),
                HttpVersionPreconditions.requireIfMatch(ifMatch), contexts.operation(request)));
        var result = progress.handle(new ProgressInterview.Command(ResourceId.of(sessionId),
                started.snapshot().version(), contexts.operation(request)));
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(result.version()))
                .body(SessionResponse.from(result));
    }

    @PostMapping(path = "/interviews/{sessionId}/answers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionResponse> submitAnswer(@PathVariable UUID sessionId,
                                                        @RequestHeader("If-Match")
                                                        @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
                                                        @Valid @RequestBody SubmitAnswerRequest body,
                                                        HttpServletRequest request) {
        var submitted = submitAnswer.handle(new SubmitInterviewAnswer.Command(
                ResourceId.of(sessionId), ResourceId.of(body.turnId()), body.turnSequence(),
                InterviewAnswerSource.TEXT, body.text(), Optional.empty(),
                HttpVersionPreconditions.requireIfMatch(ifMatch), contexts.operation(request)));
        var result = progress.handle(new ProgressInterview.Command(ResourceId.of(sessionId),
                submitted.snapshot().version(), contexts.operation(request)));
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(result.version()))
                .body(SessionResponse.from(result));
    }

    @PostMapping(path = "/interviews/{sessionId}/commands/{command}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionResponse> command(@PathVariable UUID sessionId,
                                                   @PathVariable @Pattern(regexp = "pause|resume|skip|complete|cancel|recover") String command,
                                                   @RequestHeader("If-Match")
                                                   @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
                                                   @RequestBody(required = false) SessionCommandRequest body,
                                                   HttpServletRequest request) {
        SessionCommandType type = SessionCommandType.valueOf(command.toUpperCase(java.util.Locale.ROOT));
        var changed = applyCommand.handle(new ApplySessionCommand.Command(ResourceId.of(sessionId), type,
                Optional.ofNullable(body).map(SessionCommandRequest::reasonCode).filter(value -> !value.isBlank()),
                HttpVersionPreconditions.requireIfMatch(ifMatch), contexts.operation(request)));
        var result = (type == SessionCommandType.SKIP || type == SessionCommandType.RESUME
                || type == SessionCommandType.COMPLETE)
                ? progress.handle(new ProgressInterview.Command(ResourceId.of(sessionId),
                        changed.version(), contexts.operation(request)))
                : changed;
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(result.version()))
                .body(SessionResponse.from(result));
    }

    private static InterviewMode mode(String value) {
        return "CASCADE_VOICE".equals(value) ? InterviewMode.VOICE : InterviewMode.valueOf(value);
    }

    public record CreatePlanRequest(@NotBlank String targetRole, @NotBlank String targetLevel,
                                    @NotEmpty @Size(max = 9) Set<@NotBlank String> topics,
                                    @Min(5) @Max(60) int durationMinutes, @NotBlank String mode) { }
    public record ConfirmPlanRequest(@NotBlank @Size(max = 128) String acknowledgedEstimateVersion) { }
    public record CreateSessionRequest(@NotNull UUID confirmedPlanId, @Min(1) int planVersionNo) { }
    public record SubmitAnswerRequest(@NotNull UUID turnId, @Min(1) int turnSequence,
                                      @NotBlank @Size(max = 30_000) String text) { }
    public record SessionCommandRequest(@Size(max = 96) String reasonCode) { }
    public record EstimateResponse(BigDecimalValue quantity, String unit, String estimateVersion) { }
    public record BigDecimalValue(String value) { }

    public record PlanResponse(String id, int planVersionNo, String state, String mode,
                               int questionCount, int followUpBudget, UsageResponse estimatedUsage,
                               String reservationId, String expiresAt, long version) {
        static PlanResponse from(InterviewPlanView view) {
            return new PlanResponse(view.id().value(), view.planVersionNo(), view.state().name(),
                    view.mode() == InterviewMode.VOICE ? "CASCADE_VOICE" : view.mode().name(),
                    view.questionCount(), view.followUpBudget(),
                    new UsageResponse(view.estimatedUsage().quantity().value(),
                            view.estimatedUsage().quantity().unit(), view.estimatedUsage().ruleVersion()),
                    view.reservationId().map(ResourceId::value).orElse(null), view.expiresAt().toString(),
                    view.version().value());
        }
    }
    public record UsageResponse(java.math.BigDecimal quantity, String unit, String estimateVersion) { }
    public record TurnResponse(String turnId, int sequence, String state, String questionText) { }
    public record SessionResponse(String id, String state, String mode, List<TurnResponse> turns,
                                  int lastStableTurnSequence, List<String> allowedCommands,
                                  String failureCode, long version) {
        static SessionResponse from(InterviewSessionSnapshot snapshot) {
            return new SessionResponse(snapshot.id().value(), snapshot.state().name(),
                    snapshot.mode() == InterviewMode.VOICE ? "CASCADE_VOICE" : snapshot.mode().name(),
                    snapshot.turns().stream().map(turn -> new TurnResponse(turn.turnId().value(), turn.sequence(),
                            turn.state().name(), turn.questionText().orElse(null))).toList(),
                    snapshot.lastStableTurnSequence(), snapshot.allowedCommands().stream()
                            .map(Enum::name).toList(), snapshot.failureCode().orElse(null),
                    snapshot.version().value());
        }
    }
}

