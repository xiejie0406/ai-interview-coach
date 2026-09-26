package com.ruoyi.aden.application.runner;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException;
import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.task.AdenTaskCasRepository;
import com.ruoyi.aden.application.task.AdenTaskLedgerRepository;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenReceiptType;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskActor;
import com.ruoyi.aden.domain.task.AdenTaskCommand;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 把 Runner receipt 原子裁决为 Delivery、Step、Task 与可靠事件账本。 */
public class AdenRunnerReceiptService {
    private static final String OUTBOX_CONSUMER = "OPERATOR_SSE";
    private final AdenRunnerDeliveryRepository deliveries;
    private final AdenTaskLedgerRepository tasks;
    private final AdenTaskCasRepository taskCas;
    private final AdenRequestFingerprint json;
    private final ObjectMapper objectMapper;
    private final AdenIdGenerator ids;
    private final Clock clock;
    private final int inProgressTtlSeconds;
    private final int retentionSeconds;

    public AdenRunnerReceiptService(AdenRunnerDeliveryRepository deliveries,
                                    AdenTaskLedgerRepository tasks,
                                    AdenTaskCasRepository taskCas,
                                    AdenRequestFingerprint json,
                                    ObjectMapper objectMapper,
                                    AdenIdGenerator ids, Clock clock,
                                    int inProgressTtlSeconds, int retentionSeconds) {
        this.deliveries = Objects.requireNonNull(deliveries);
        this.tasks = Objects.requireNonNull(tasks);
        this.taskCas = Objects.requireNonNull(taskCas);
        this.json = Objects.requireNonNull(json);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        if (inProgressTtlSeconds < 1 || retentionSeconds < inProgressTtlSeconds) {
            throw new IllegalArgumentException("Receipt 幂等配置无效");
        }
        this.inProgressTtlSeconds = inProgressTtlSeconds;
        this.retentionSeconds = retentionSeconds;
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public ReceiptResult apply(ReceiptCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.receiptSequence() < 1 || command.fenceToken() < 1) {
            throw new IllegalArgumentException("receipt sequence 与 fence 必须为正数");
        }
        Instant now = clock.instant();
        String payloadJson = json.canonicalJson(command.payload());
        if (payloadJson.length() > 60000) throw new IllegalArgumentException("receipt payload 过大");
        String requestHash = json.hashValue(Map.of(
                "workspaceId", command.principal().workspaceId().value(),
                "sessionId", command.principal().sessionId().value(),
                "sessionEpoch", Long.toString(command.principal().sessionEpoch()),
                "deliveryId", command.deliveryId().value(),
                "receiptId", command.receiptId(),
                "fenceToken", Long.toString(command.fenceToken()),
                "receiptSequence", Long.toString(command.receiptSequence()),
                "type", command.type().name(), "observedAt", command.observedAt().toString(),
                "payload", command.payload()));

        AdenRunnerDeliveryRepository.ReceiptRoute route = deliveries.findReceiptRoute(
                command.principal().workspaceId(), command.deliveryId()).orElseThrow(AdenNotFoundException::new);
        long workspaceSequence = deliveries.lockWorkspace(command.principal().workspaceId());
        var existing = deliveries.findReceiptInboxForUpdate(command.principal().workspaceId(),
                command.principal().sessionId(), command.idempotencyKey());
        if (existing.isPresent()) {
            var inbox = existing.get();
            if (!inbox.requestHash().equals(requestHash)) throw new AdenIdempotencyKeyReusedException();
            if ("COMPLETED".equals(inbox.state()) && inbox.responseJson() != null) {
                return decode(inbox.responseJson(), true);
            }
            throw new AdenApplicationException("ADEN_DEPENDENCY_UNAVAILABLE", "相同 receipt 仍在处理中");
        }
        String inboxId = ids.nextId();
        deliveries.insertReceiptInbox(new AdenRunnerDeliveryRepository.ClaimInbox(
                command.principal().workspaceId(), inboxId, command.principal().sessionId(),
                command.idempotencyKey(), requestHash, "IN_PROGRESS", null,
                now.plusSeconds(inProgressTtlSeconds), now.plusSeconds(retentionSeconds), now));

        var session = deliveries.lockSession(command.principal()).orElseThrow(this::invalidSession);
        if (session.runnerCurrentEpoch() != command.principal().sessionEpoch()
                || session.epoch() != command.principal().sessionEpoch()) {
            throw new AdenApplicationException("ADEN_SESSION_EPOCH_STALE",
                    "Runner principal 已被更新的 Session epoch 取代");
        }
        if (!"ACTIVE".equals(session.status()) || !session.expiresAt().isAfter(session.databaseNow())) {
            throw invalidSession();
        }

        AdenTaskLedgerRepository.StoredTask stored = tasks.findTaskForUpdate(
                command.principal().workspaceId(), new AdenTaskId(route.taskId()))
                .orElseThrow(AdenNotFoundException::new);
        var step = deliveries.lockReceiptStep(command.principal().workspaceId(), route.stepId())
                .orElseThrow(AdenNotFoundException::new);
        var delivery = deliveries.lockReceiptDelivery(command.principal().workspaceId(), command.deliveryId())
                .orElseThrow(AdenNotFoundException::new);
        validateOwnership(command, route, step, delivery);

        ReceiptPlan plan = plan(command.type(), stored.task().state(), step.state(), delivery.state(),
                delivery.cancelRequestedAt() != null);
        if (command.type() == AdenReceiptType.FAILED_RETRYABLE) {
            RetryPackage retry = retryPackage(delivery);
            deliveries.retryAfterReceipt(delivery, ids.nextId(), retry.json(), retry.hash(),
                    command.receiptSequence(), payloadJson, plan.errorCode(), plan.errorMessage());
        } else {
            deliveries.applyReceiptDelivery(delivery, plan.deliveryState(), command.receiptSequence(),
                    terminal(command.type()) ? payloadJson : null, plan.errorCode(), plan.errorMessage());
        }
        deliveries.applyReceiptStep(step, plan.stepState(), payloadJson,
                plan.errorCode(), plan.errorMessage());

        AdenTask resultTask = stored.task();
        if (plan.taskCommand() != null) {
            var transition = stored.task().transition(AdenTaskActor.RUNNER, plan.taskCommand(), now);
            taskCas.updateState(stored.task(), transition.task());
            resultTask = transition.task();
        } else {
            resultTask = stored.task().advanceContentVersion(now);
            taskCas.updateState(stored.task(), resultTask);
        }
        if (terminal(command.type())) deliveries.decreaseInFlight(command.principal(), 1);

        long nextSequence = Math.addExact(workspaceSequence, 1);
        tasks.advanceWorkspaceEventSequence(command.principal().workspaceId(), workspaceSequence, nextSequence, now);
        String eventId = ids.nextId();
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        String eventType;
        if (command.type() == AdenReceiptType.PROGRESS) {
            eventType = "aden.task.progressed.v1";
            Object progress = command.payload().get("progressPercent");
            eventPayload.put("progressPercent", progress instanceof Number ? progress : 0);
        } else {
            eventType = "aden.task.state-changed.v1";
            eventPayload.put("from", stored.task().state().name());
            eventPayload.put("to", resultTask.state().name());
            eventPayload.put("reasonCode", command.type().name());
        }
        tasks.insertEvent(new AdenTaskLedgerRepository.LedgerEvent(
                command.principal().workspaceId(), eventId, nextSequence, resultTask.id(),
                resultTask.version().value(), eventType,
                json.canonicalJson(eventPayload), "RUNNER", command.principal().runnerId().value(),
                command.correlationId(), now));
        tasks.insertOutbox(new AdenTaskLedgerRepository.OutboxMessage(command.principal().workspaceId(),
                ids.nextId(), eventId, OUTBOX_CONSUMER, now));
        tasks.insertAudit(new AdenTaskLedgerRepository.TaskAudit(command.principal().workspaceId(),
                ids.nextId(), "RUNNER_RECEIPT_ACCEPTED", resultTask.id(), "RUNNER",
                command.principal().runnerId().value(), command.correlationId(),
                json.canonicalJson(Map.of("deliveryId", command.deliveryId().value(),
                        "receiptType", command.type().name())), now));
        ReceiptResult result = new ReceiptResult(command.receiptId(), command.deliveryId().value(),
                route.taskId(), command.receiptSequence(), plan.deliveryState(), plan.stepState(),
                resultTask.state().name(), resultTask.version().value(), now,
                command.correlationId(), false);
        deliveries.completeReceiptInbox(command.principal().workspaceId(), inboxId, encode(result), now);
        return result;
    }

    private void validateOwnership(ReceiptCommand command,
                                   AdenRunnerDeliveryRepository.ReceiptRoute route,
                                   AdenRunnerDeliveryRepository.ReceiptStep step,
                                   AdenRunnerDeliveryRepository.ReceiptDelivery delivery) {
        if (!route.taskId().equals(step.taskId()) || !route.stepId().equals(step.stepId())
                || !route.taskId().equals(delivery.taskId()) || !route.stepId().equals(delivery.stepId())) {
            throw new AdenApplicationException("ADEN_RECEIPT_STALE", "Receipt 路由已改变");
        }
        if (!command.principal().sessionId().value().equals(delivery.ownerSessionId())
                || command.principal().sessionEpoch() != delivery.ownerSessionEpoch()
                || command.fenceToken() != delivery.fenceToken()) {
            throw new AdenApplicationException("ADEN_RECEIPT_STALE", "Receipt owner、epoch 或 fence 失效");
        }
        if (delivery.leaseUntil() == null || !delivery.leaseUntil().isAfter(delivery.databaseNow())) {
            throw new AdenApplicationException("ADEN_RUNNER_LEASE_LOST", "Receipt 对应 lease 已过期");
        }
        if (command.receiptSequence() != delivery.latestReceiptSequence() + 1) {
            throw new AdenApplicationException("ADEN_RECEIPT_STALE", "receipt sequence 必须严格递增");
        }
    }

    private ReceiptPlan plan(AdenReceiptType type, AdenTaskState task, String step,
                             String delivery, boolean cancelRequested) {
        return switch (type) {
            case STARTED -> task == AdenTaskState.QUEUED
                    ? require("READY".equals(step) && "LEASED".equals(delivery),
                            new ReceiptPlan("RUNNING", "RUNNING", AdenTaskCommand.START, null, null))
                    : require(task == AdenTaskState.RUNNING && "READY".equals(step)
                                    && "LEASED".equals(delivery),
                            new ReceiptPlan("RUNNING", "RUNNING", null, null, null));
            case PROGRESS -> require(task == AdenTaskState.RUNNING && "RUNNING".equals(step)
                            && "RUNNING".equals(delivery),
                    new ReceiptPlan("RUNNING", "RUNNING", null, null, null));
            case COMPLETED -> require(task == AdenTaskState.RUNNING && "RUNNING".equals(step)
                            && "RUNNING".equals(delivery),
                    new ReceiptPlan("COMPLETED", "SUCCEEDED", AdenTaskCommand.COMPLETE, null, null));
            case FAILED_FINAL -> require(task == AdenTaskState.RUNNING
                            && "RUNNING".equals(step) && "RUNNING".equals(delivery),
                    new ReceiptPlan("FAILED_FINAL", "FAILED", AdenTaskCommand.FAIL,
                            "SYNTHETIC_RUNNER_FAILURE", "合成 Runner 报告最终失败"));
            case FAILED_RETRYABLE -> require(task == AdenTaskState.RUNNING
                            && "RUNNING".equals(step) && "RUNNING".equals(delivery)
                            && !cancelRequested,
                    new ReceiptPlan("FAILED_RETRYABLE", "READY", null,
                            "SYNTHETIC_RETRYABLE", "合成 Runner 报告可重试失败"));
            case CANCELED_SAFE_POINT -> require(task == AdenTaskState.CANCEL_REQUESTED && cancelRequested
                            && ("READY".equals(step) || "RUNNING".equals(step))
                            && ("LEASED".equals(delivery) || "RUNNING".equals(delivery)),
                    new ReceiptPlan("CANCELED", "CANCELED", AdenTaskCommand.CONFIRM_CANCELED, null, null));
            case OUTCOME_UNKNOWN -> require(task == AdenTaskState.CANCEL_REQUESTED && cancelRequested
                            && ("READY".equals(step) || "RUNNING".equals(step))
                            && ("LEASED".equals(delivery) || "RUNNING".equals(delivery)),
                    new ReceiptPlan("OUTCOME_UNKNOWN", "OUTCOME_UNKNOWN", null,
                            "ADEN_OUTCOME_UNKNOWN", "Runner 无法确认合成执行终态"));
        };
    }

    private ReceiptPlan require(boolean condition, ReceiptPlan plan) {
        if (!condition) throw new AdenApplicationException("ADEN_RECEIPT_STALE",
                "Receipt 与当前 Task、Step 或 Delivery 状态不兼容");
        return plan;
    }

    private static boolean terminal(AdenReceiptType type) {
        return type == AdenReceiptType.COMPLETED || type == AdenReceiptType.FAILED_FINAL
                || type == AdenReceiptType.FAILED_RETRYABLE || type == AdenReceiptType.CANCELED_SAFE_POINT
                || type == AdenReceiptType.OUTCOME_UNKNOWN;
    }

    private RetryPackage retryPackage(AdenRunnerDeliveryRepository.ReceiptDelivery delivery) {
        try {
            ObjectNode packageBody = (ObjectNode) objectMapper.readTree(delivery.taskPackageJson());
            packageBody.remove("packageHash");
            packageBody.put("attemptNo", delivery.attemptNo() + 1);
            packageBody.put("deadlineAt", clock.instant().plusSeconds(3600)
                    .truncatedTo(ChronoUnit.MICROS).toString());
            String hash = json.hashValue(packageBody);
            packageBody.put("packageHash", hash);
            return new RetryPackage(json.canonicalJson(packageBody), hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Runner 回执无法生成 retry TaskPackage", exception);
        }
    }

    private String encode(ReceiptResult result) {
        return json.canonicalJson(new Replay(result.receiptId(), result.deliveryId(), result.taskId(),
                result.receiptSequence(), result.deliveryState(), result.stepState(), result.taskState(),
                result.taskVersion(), result.acceptedAt().toString(), result.correlationId()));
    }

    private ReceiptResult decode(String value, boolean replayed) {
        try {
            Replay replay = objectMapper.readValue(value, Replay.class);
            return new ReceiptResult(replay.receiptId(), replay.deliveryId(), replay.taskId(),
                    replay.receiptSequence(), replay.deliveryState(), replay.stepState(), replay.taskState(),
                    replay.taskVersion(), Instant.parse(replay.acceptedAt()), replay.correlationId(), replayed);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Receipt Inbox 回放结果无法解析", exception);
        }
    }

    private AdenApplicationException invalidSession() {
        return new AdenApplicationException("ADEN_RUNNER_SESSION_INVALID", "Runner session 无效或已过期");
    }

    public record ReceiptCommand(AdenRunnerSessionPrincipal principal,
                                 AdenIdempotencyKey idempotencyKey,
                                 AdenDeliveryId deliveryId,
                                 String receiptId,
                                 long fenceToken,
                                 long receiptSequence,
                                 AdenReceiptType type,
                                 Instant observedAt,
                                 Map<String, Object> payload,
                                 String correlationId) {
        public ReceiptCommand {
            Objects.requireNonNull(principal); Objects.requireNonNull(idempotencyKey);
            Objects.requireNonNull(deliveryId); Objects.requireNonNull(receiptId);
            Objects.requireNonNull(type); Objects.requireNonNull(observedAt);
            payload = Map.copyOf(Objects.requireNonNull(payload));
            Objects.requireNonNull(correlationId);
        }

        public ReceiptCommand(AdenRunnerSessionPrincipal principal,
                              AdenIdempotencyKey idempotencyKey,
                              AdenDeliveryId deliveryId,
                              long fenceToken,
                              long receiptSequence,
                              AdenReceiptType type,
                              Map<String, Object> payload,
                              String correlationId) {
            this(principal, idempotencyKey, deliveryId, correlationId, fenceToken,
                    receiptSequence, type, Instant.EPOCH, payload, correlationId);
        }
    }

    public record ReceiptResult(String receiptId, String deliveryId, String taskId,
                                long receiptSequence, String deliveryState,
                                String stepState, String taskState, long taskVersion,
                                Instant acceptedAt, String correlationId,
                                boolean replayed) { }
    private record Replay(String receiptId, String deliveryId, String taskId,
                          long receiptSequence, String deliveryState,
                          String stepState, String taskState, long taskVersion,
                          String acceptedAt, String correlationId) { }
    private record ReceiptPlan(String deliveryState, String stepState, AdenTaskCommand taskCommand,
                               String errorCode, String errorMessage) { }
    private record RetryPackage(String json, String hash) { }
}
