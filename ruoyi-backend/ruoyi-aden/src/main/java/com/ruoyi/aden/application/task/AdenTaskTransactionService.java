package com.ruoyi.aden.application.task;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException;
import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.error.AdenVersionConflictException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.workspace.AdenWorkspaceAccessGuard;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskActor;
import com.ruoyi.aden.domain.task.AdenTaskCommand;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskStep;
import com.ruoyi.aden.domain.task.AdenTaskStepId;
import com.ruoyi.aden.domain.task.AdenTaskStepState;
import com.ruoyi.aden.domain.task.AdenTaskTransition;
import com.ruoyi.aden.domain.task.AdenTaskType;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Task 写用例的唯一事务入口；不执行网络、SSE send、Runner 或 Provider 调用。 */
public class AdenTaskTransactionService {
    public static final String CREATE_PERMISSION = "aden:task:create";
    public static final String COMMAND_PERMISSION = "aden:task:command";
    public static final String CANCEL_PERMISSION = "aden:task:cancel";

    private static final String CREATE_OPERATION = "CREATE_TASK";
    private static final String SUBMIT_OPERATION = "SUBMIT_FOR_VALIDATION";
    private static final String CANCEL_OPERATION = "REQUEST_CANCEL";
    private static final String OUTBOX_CONSUMER = "OPERATOR_SSE";

    private final AdenTaskLedgerRepository ledger;
    private final AdenTaskCasRepository casRepository;
    private final AdenWorkspaceAccessGuard accessGuard;
    private final AdenRequestFingerprint fingerprint;
    private final AdenSyntheticTaskValidator validator;
    private final AdenIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int inProgressTtlSeconds;
    private final int retentionSeconds;

    public AdenTaskTransactionService(
            AdenTaskLedgerRepository ledger,
            AdenTaskCasRepository casRepository,
            AdenWorkspaceAccessGuard accessGuard,
            AdenRequestFingerprint fingerprint,
            AdenSyntheticTaskValidator validator,
            AdenIdGenerator idGenerator,
            ObjectMapper objectMapper,
            Clock clock,
            int inProgressTtlSeconds,
            int retentionSeconds) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.casRepository = Objects.requireNonNull(casRepository, "casRepository");
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (inProgressTtlSeconds < 1) throw new IllegalArgumentException("inProgressTtlSeconds 必须为正数");
        if (retentionSeconds < inProgressTtlSeconds) {
            throw new IllegalArgumentException("retentionSeconds 不得小于 inProgressTtlSeconds");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inProgressTtlSeconds = inProgressTtlSeconds;
        this.retentionSeconds = retentionSeconds;
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public AdenTaskResult createTask(CreateTask command) {
        Objects.requireNonNull(command, "command");
        accessGuard.requireWorkspace(command.principal(), CREATE_PERMISSION, command.workspaceId(),
                AdenWorkspaceAccessGuard.WRITE_ROLES, command.correlationId().value());
        Instant now = clock.instant();
        String producerId = Long.toString(command.principal().userId());
        String normalizedTitle = AdenTask.normalizeTitle(command.title());
        String inputJson = fingerprint.canonicalJson(command.input());
        String requestHash = fingerprint.hashValue(Map.of(
                "workspaceId", command.workspaceId().value(),
                "actorUserId", producerId,
                "taskType", AdenTaskType.SYNTHETIC_CORE.name(),
                "capabilityCode", AdenCapabilityCode.CORE.name(),
                "title", normalizedTitle,
                "input", command.input()));

        long sequence = ledger.lockWorkspaceEventSequence(command.workspaceId());
        InboxResolution inbox = openInboxOrReplay(command.workspaceId(), producerId, CREATE_OPERATION,
                command.idempotencyKey(), requestHash, now);
        if (inbox.replay() != null) return inbox.replay();

        String inboxId = inbox.inboxId();
        AdenTask task = new AdenTask(
                command.workspaceId(), new AdenTaskId(idGenerator.nextId()), AdenTaskType.SYNTHETIC_CORE,
                AdenCapabilityCode.CORE, normalizedTitle, AdenTaskState.DRAFT, new AdenTaskVersion(1),
                command.correlationId(), command.principal().userId(), now, now);
        AdenTaskStep step = new AdenTaskStep(
                new AdenTaskStepId(idGenerator.nextId()), 1, 1, AdenTaskStepState.PENDING, 0);
        ledger.insertTask(task, inputJson, command.idempotencyKey(), requestHash);
        ledger.insertStep(command.workspaceId(), task.id(), step, inputJson, now);
        sequence = appendEvent(sequence, task, "aden.task.created.v1", "OPERATOR", producerId,
                command.correlationId().value(),
                Map.of("to", task.state().name()), now);
        ledger.insertAudit(new AdenTaskLedgerRepository.TaskAudit(
                command.workspaceId(), idGenerator.nextId(), "TASK_CREATED", task.id(), "OPERATOR", producerId,
                command.correlationId().value(), "{}", now));
        AdenTaskResult result = result(task, null, false);
        ledger.completeInbox(command.workspaceId(), inboxId, 201, encodeReplay(result), now);
        return result;
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public AdenTaskResult submitForValidation(SubmitForValidation command) {
        Objects.requireNonNull(command, "command");
        accessGuard.requireWorkspace(command.principal(), COMMAND_PERMISSION, command.workspaceId(),
                AdenWorkspaceAccessGuard.WRITE_ROLES, command.correlationId().value());
        Instant now = clock.instant();
        String producerId = Long.toString(command.principal().userId());
        String requestHash = fingerprint.hashValue(Map.of(
                "workspaceId", command.workspaceId().value(),
                "taskId", command.taskId().value(),
                "actorUserId", producerId,
                "command", SUBMIT_OPERATION,
                "expectedVersion", Long.toString(command.expectedVersion().value())));

        long sequence = ledger.lockWorkspaceEventSequence(command.workspaceId());
        InboxResolution inbox = openInboxOrReplay(command.workspaceId(), producerId, SUBMIT_OPERATION,
                command.idempotencyKey(), requestHash, now);
        if (inbox.replay() != null) return inbox.replay();
        String inboxId = inbox.inboxId();

        AdenTaskLedgerRepository.StoredTask stored = ledger.findTaskForUpdate(
                command.workspaceId(), command.taskId()).orElseThrow(AdenNotFoundException::new);
        AdenTask current = stored.task();
        if (current.version().value() != command.expectedVersion().value()) {
            throw new AdenVersionConflictException(
                    Long.toString(command.expectedVersion().value()), current.state().name());
        }
        AdenTaskTransition validating = current.transition(
                AdenTaskActor.OPERATOR, AdenTaskCommand.SUBMIT_FOR_VALIDATION, now);
        casRepository.updateState(current, validating.task());
        sequence = appendTransition(sequence, validating, "OPERATOR", producerId,
                command.correlationId().value(), now);

        AdenSyntheticTaskInput input = decodeInput(stored.inputJson());
        AdenSyntheticTaskValidator.ValidationResult validation = validator.validate(
                current.type(), current.capability(), input);
        AdenTaskCommand validationCommand = validation.passed()
                ? AdenTaskCommand.VALIDATION_PASSED : AdenTaskCommand.VALIDATION_FAILED;
        AdenTaskTransition decided = validating.task().transition(
                AdenTaskActor.VALIDATOR, validationCommand, now);
        if (validation.passed()) {
            casRepository.updateState(validating.task(), decided.task());
            ledger.markFirstStepReady(command.workspaceId(), command.taskId(), now);
            AdenTaskStepId stepId = ledger.findFirstStepIdForUpdate(command.workspaceId(), command.taskId());
            Map<String, Object> packageBody = new LinkedHashMap<>();
            packageBody.put("schemaVersion", 1);
            packageBody.put("taskId", command.taskId().value());
            packageBody.put("stepId", stepId.value());
            packageBody.put("taskType", current.type().name());
            packageBody.put("capabilityCode", current.capability().name());
            packageBody.put("attemptNo", 1);
            packageBody.put("input", input);
            packageBody.put("externalActionsEnabled", false);
            // Runner 使用 Python datetime 解析后重建规范 JSON；线级时间固定为 MySQL 同等的微秒精度。
            packageBody.put("deadlineAt", now.plusSeconds(3600).truncatedTo(ChronoUnit.MICROS).toString());
            String packageHash = fingerprint.hashValue(packageBody);
            packageBody.put("packageHash", packageHash);
            ledger.insertReadyDelivery(command.workspaceId(), idGenerator.nextId(), command.taskId(),
                    stepId, fingerprint.canonicalJson(packageBody), packageHash, now);
        } else {
            ledger.updateValidationFailureCas(
                    validating.task(), decided.task(), validation.reasonCode());
        }
        appendTransition(sequence, decided, "SYSTEM", "SYNTHETIC_VALIDATOR",
                command.correlationId().value(), now);
        String detailsJson = fingerprint.canonicalJson(Map.of(
                "validation", validation.passed() ? "PASSED" : "FAILED"));
        ledger.insertAudit(new AdenTaskLedgerRepository.TaskAudit(
                command.workspaceId(), idGenerator.nextId(), "TASK_SUBMITTED_FOR_VALIDATION",
                command.taskId(), "OPERATOR", producerId, command.correlationId().value(), detailsJson, now));
        AdenTaskResult result = result(decided.task(), validation.reasonCode(), false);
        ledger.completeInbox(command.workspaceId(), inboxId, 200, encodeReplay(result), now);
        return result;
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public AdenTaskResult requestCancel(RequestCancel command) {
        Objects.requireNonNull(command, "command");
        accessGuard.requireWorkspace(command.principal(), CANCEL_PERMISSION, command.workspaceId(),
                AdenWorkspaceAccessGuard.WRITE_ROLES, command.correlationId().value());
        Instant now = clock.instant();
        String producerId = Long.toString(command.principal().userId());
        String requestHash = fingerprint.hashValue(Map.of(
                "workspaceId", command.workspaceId().value(),
                "taskId", command.taskId().value(),
                "actorUserId", producerId,
                "command", CANCEL_OPERATION,
                "expectedVersion", Long.toString(command.expectedVersion().value())));

        long sequence = ledger.lockWorkspaceEventSequence(command.workspaceId());
        InboxResolution inbox = openInboxOrReplay(command.workspaceId(), producerId, CANCEL_OPERATION,
                command.idempotencyKey(), requestHash, now);
        if (inbox.replay() != null) return inbox.replay();
        AdenTask current = ledger.findTaskForUpdate(command.workspaceId(), command.taskId())
                .orElseThrow(AdenNotFoundException::new).task();
        if (current.version().value() != command.expectedVersion().value()) {
            throw new AdenVersionConflictException(
                    Long.toString(command.expectedVersion().value()), current.state().name());
        }
        AdenTaskTransition canceled = current.transition(
                AdenTaskActor.OPERATOR, AdenTaskCommand.REQUEST_CANCEL, now);
        casRepository.updateState(current, canceled.task());
        sequence = appendTransition(sequence, canceled, "OPERATOR", producerId,
                command.correlationId().value(), now);
        ledger.requestCancelDeliveries(command.workspaceId(), command.taskId(), now);
        ledger.cancelReadySteps(command.workspaceId(), command.taskId(), now);
        AdenTask finalTask = canceled.task();
        if (ledger.countActiveDeliveries(command.workspaceId(), command.taskId()) == 0) {
            AdenTaskTransition confirmed = canceled.task().transition(
                    AdenTaskActor.COORDINATOR, AdenTaskCommand.CONFIRM_CANCELED, now);
            casRepository.updateState(canceled.task(), confirmed.task());
            appendTransition(sequence, confirmed, "SYSTEM", "CANCEL_COORDINATOR",
                    command.correlationId().value(), now);
            finalTask = confirmed.task();
        }
        ledger.insertAudit(new AdenTaskLedgerRepository.TaskAudit(
                command.workspaceId(), idGenerator.nextId(), "TASK_CANCEL_REQUESTED",
                command.taskId(), "OPERATOR", producerId, command.correlationId().value(), "{}", now));
        AdenTaskResult result = result(finalTask, null, false);
        ledger.completeInbox(command.workspaceId(), inbox.inboxId(), 200, encodeReplay(result), now);
        return result;
    }

    private InboxResolution openInboxOrReplay(AdenWorkspaceId workspaceId, String producerId,
                                              String operation, AdenIdempotencyKey key,
                                              String requestHash, Instant now) {
        var existing = ledger.findInboxForUpdate(workspaceId, producerId, operation, key);
        if (existing.isPresent()) {
            AdenTaskLedgerRepository.InboxFact fact = existing.get();
            if (!fact.requestHash().equals(requestHash)) throw new AdenIdempotencyKeyReusedException();
            if ("COMPLETED".equals(fact.state()) && fact.responseJson() != null) {
                return new InboxResolution(null, decodeReplay(fact.responseJson()));
            }
            throw new AdenApplicationException(
                    "ADEN_DEPENDENCY_UNAVAILABLE", "相同幂等请求仍在处理中");
        }
        String inboxId = idGenerator.nextId();
        ledger.insertInbox(new AdenTaskLedgerRepository.InboxFact(
                workspaceId, inboxId, producerId, operation, key, requestHash, "IN_PROGRESS", null,
                now.plusSeconds(inProgressTtlSeconds), now.plusSeconds(retentionSeconds), now));
        return new InboxResolution(inboxId, null);
    }

    private long appendTransition(long sequence, AdenTaskTransition transition,
                                  String actorType, String actorId, String correlationId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", transition.event().from().name());
        payload.put("to", transition.event().to().name());
        payload.put("reasonCode", transition.event().command().name());
        return appendEvent(sequence, transition.task(), "aden.task.state-changed.v1",
                actorType, actorId, correlationId, payload, now);
    }

    private long appendEvent(long currentSequence, AdenTask task, String eventType,
                             String actorType, String actorId, String correlationId,
                             Object payload, Instant now) {
        if (currentSequence == Long.MAX_VALUE) throw new IllegalStateException("Workspace event sequence 已耗尽");
        long nextSequence = currentSequence + 1;
        ledger.advanceWorkspaceEventSequence(task.workspaceId(), currentSequence, nextSequence, now);
        String eventId = idGenerator.nextId();
        ledger.insertEvent(new AdenTaskLedgerRepository.LedgerEvent(
                task.workspaceId(), eventId, nextSequence, task.id(), task.version().value(), eventType,
                fingerprint.canonicalJson(payload), actorType, actorId, correlationId, now));
        ledger.insertOutbox(new AdenTaskLedgerRepository.OutboxMessage(
                task.workspaceId(), idGenerator.nextId(), eventId, OUTBOX_CONSUMER, now));
        return nextSequence;
    }

    private AdenSyntheticTaskInput decodeInput(String json) {
        try {
            return objectMapper.readValue(json, AdenSyntheticTaskInput.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("数据库中的合成 Task input 无法解析", exception);
        }
    }

    private String encodeReplay(AdenTaskResult result) {
        return fingerprint.canonicalJson(new ReplayPayload(
                result.workspaceId().value(), result.taskId().value(), result.state().name(),
                Long.toString(result.version().value()), result.reasonCode()));
    }

    private AdenTaskResult decodeReplay(String json) {
        try {
            ReplayPayload payload = objectMapper.readValue(json, ReplayPayload.class);
            return new AdenTaskResult(new AdenWorkspaceId(payload.workspaceId()),
                    new AdenTaskId(payload.taskId()), AdenTaskState.valueOf(payload.state()),
                    new AdenTaskVersion(Long.parseLong(payload.version())), payload.reasonCode(), true);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Inbox 回放结果无法解析", exception);
        }
    }

    private static AdenTaskResult result(AdenTask task, String reasonCode, boolean replayed) {
        return new AdenTaskResult(task.workspaceId(), task.id(), task.state(), task.version(), reasonCode, replayed);
    }

    public record CreateTask(
            AdenOperatorPrincipal principal,
            AdenWorkspaceId workspaceId,
            AdenIdempotencyKey idempotencyKey,
            String title,
            AdenSyntheticTaskInput input,
            AdenCorrelationId correlationId) { }

    public record SubmitForValidation(
            AdenOperatorPrincipal principal,
            AdenWorkspaceId workspaceId,
            AdenTaskId taskId,
            AdenTaskVersion expectedVersion,
            AdenIdempotencyKey idempotencyKey,
            AdenCorrelationId correlationId) { }

    public record RequestCancel(
            AdenOperatorPrincipal principal,
            AdenWorkspaceId workspaceId,
            AdenTaskId taskId,
            AdenTaskVersion expectedVersion,
            AdenIdempotencyKey idempotencyKey,
            AdenCorrelationId correlationId) { }

    private record ReplayPayload(
            String workspaceId,
            String taskId,
            String state,
            String version,
            String reasonCode) { }

    private record InboxResolution(String inboxId, AdenTaskResult replay) { }
}
