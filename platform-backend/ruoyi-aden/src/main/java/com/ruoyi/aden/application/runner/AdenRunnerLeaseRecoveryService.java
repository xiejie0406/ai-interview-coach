package com.ruoyi.aden.application.runner;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.task.AdenTaskCasRepository;
import com.ruoyi.aden.application.task.AdenTaskLedgerRepository;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按 Workspace→Session→Task→Step→Delivery 锁序恢复过期合成租约。 */
public class AdenRunnerLeaseRecoveryService {
    private final AdenRunnerDeliveryRepository deliveries;
    private final AdenTaskLedgerRepository tasks;
    private final AdenTaskCasRepository taskCas;
    private final AdenRequestFingerprint json;
    private final ObjectMapper objectMapper;
    private final AdenIdGenerator ids;
    private final Clock clock;

    public AdenRunnerLeaseRecoveryService(AdenRunnerDeliveryRepository deliveries,
                                          AdenTaskLedgerRepository tasks,
                                          AdenTaskCasRepository taskCas,
                                          AdenRequestFingerprint json,
                                          ObjectMapper objectMapper,
                                          AdenIdGenerator ids, Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries);
        this.tasks = Objects.requireNonNull(tasks);
        this.taskCas = Objects.requireNonNull(taskCas);
        this.json = Objects.requireNonNull(json);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public RecoveryResult recover(AdenWorkspaceId workspaceId, int limit) {
        Objects.requireNonNull(workspaceId);
        if (limit < 1 || limit > 32) throw new IllegalArgumentException("recovery limit 必须在 1..32");
        long workspaceSequence = deliveries.lockWorkspace(workspaceId);
        List<AdenRunnerDeliveryRepository.RecoveryRoute> routes = deliveries
                .findExpiredRoutes(workspaceId, limit).stream()
                .sorted(Comparator.comparing(AdenRunnerDeliveryRepository.RecoveryRoute::sessionId)
                        .thenComparing(AdenRunnerDeliveryRepository.RecoveryRoute::taskId)
                        .thenComparing(route -> route.deliveryId().value()))
                .toList();
        Map<String, AdenRunnerSessionPrincipal> principals = new LinkedHashMap<>();
        for (var route : routes) {
            principals.computeIfAbsent(route.sessionId(), ignored -> new AdenRunnerSessionPrincipal(
                    workspaceId, new AdenRunnerId(route.runnerId()),
                    new AdenSessionId(route.sessionId()), route.sessionEpoch()));
        }
        principals.values().forEach(deliveries::lockSession);

        Map<String, AdenTaskLedgerRepository.StoredTask> storedTasks = new LinkedHashMap<>();
        routes.stream().map(AdenRunnerDeliveryRepository.RecoveryRoute::taskId).distinct().sorted()
                .forEach(taskId -> tasks.findTaskForUpdate(workspaceId, new AdenTaskId(taskId))
                        .ifPresent(task -> storedTasks.put(taskId, task)));
        Map<String, AdenRunnerDeliveryRepository.ReceiptStep> steps = new LinkedHashMap<>();
        routes.stream().map(AdenRunnerDeliveryRepository.RecoveryRoute::stepId).distinct().sorted()
                .forEach(stepId -> deliveries.lockReceiptStep(workspaceId, stepId)
                        .ifPresent(step -> steps.put(stepId, step)));
        Map<String, AdenRunnerDeliveryRepository.ReceiptDelivery> lockedDeliveries = new LinkedHashMap<>();
        routes.stream().map(AdenRunnerDeliveryRepository.RecoveryRoute::deliveryId)
                .sorted(Comparator.comparing(value -> value.value()))
                .forEach(deliveryId -> deliveries.lockReceiptDelivery(workspaceId, deliveryId)
                        .ifPresent(delivery -> lockedDeliveries.put(deliveryId.value(), delivery)));

        int requeued = 0;
        int retried = 0;
        int outcomeUnknown = 0;
        for (var route : routes) {
            var delivery = lockedDeliveries.get(route.deliveryId().value());
            var step = steps.get(route.stepId());
            var stored = storedTasks.get(route.taskId());
            if (delivery == null || step == null || stored == null || delivery.leaseUntil() == null
                    || delivery.databaseNow() == null
                    || delivery.leaseUntil().isAfter(delivery.databaseNow())
                    || !("LEASED".equals(delivery.state()) || "RUNNING".equals(delivery.state()))) {
                continue;
            }
            AdenRunnerSessionPrincipal principal = principals.get(route.sessionId());
            String recovery;
            if (stored.task().state() == AdenTaskState.CANCEL_REQUESTED
                    || delivery.cancelRequestedAt() != null) {
                deliveries.expireAsOutcomeUnknown(delivery);
                deliveries.updateRecoveryStep(step, "OUTCOME_UNKNOWN", "ADEN_OUTCOME_UNKNOWN");
                outcomeUnknown++;
                recovery = "OUTCOME_UNKNOWN";
            } else if ("LEASED".equals(delivery.state())) {
                deliveries.requeueExpiredUnstarted(delivery);
                requeued++;
                recovery = "REQUEUED_SAME_DELIVERY";
            } else {
                RetryPackage retry = retryPackage(delivery);
                deliveries.retryExpiredStarted(delivery, ids.nextId(), retry.json(), retry.hash());
                deliveries.updateRecoveryStep(step, "READY", null);
                retried++;
                recovery = "RETRY_ATTEMPT_CREATED";
            }
            deliveries.decreaseInFlight(principal, 1);
            Instant occurredAt = clock.instant().isBefore(stored.task().updatedAt())
                    ? stored.task().updatedAt() : clock.instant();
            AdenTask updatedTask = stored.task().advanceContentVersion(occurredAt);
            taskCas.updateState(stored.task(), updatedTask);
            storedTasks.put(route.taskId(), new AdenTaskLedgerRepository.StoredTask(updatedTask, stored.inputJson()));
            long nextSequence = Math.addExact(workspaceSequence, 1);
            tasks.advanceWorkspaceEventSequence(workspaceId, workspaceSequence, nextSequence, occurredAt);
            String eventId = ids.nextId();
            tasks.insertEvent(new AdenTaskLedgerRepository.LedgerEvent(workspaceId, eventId,
                    nextSequence, updatedTask.id(), updatedTask.version().value(),
                    "aden.task.progressed.v1", json.canonicalJson(Map.of(
                    "reasonCode", "LEASE_" + recovery)),
                    "SYSTEM", "LEASE_RECOVERY", ids.nextId(), occurredAt));
            tasks.insertOutbox(new AdenTaskLedgerRepository.OutboxMessage(
                    workspaceId, ids.nextId(), eventId, "OPERATOR_SSE", occurredAt));
            tasks.insertAudit(new AdenTaskLedgerRepository.TaskAudit(workspaceId, ids.nextId(),
                    "RUNNER_LEASE_RECOVERED", updatedTask.id(), "SYSTEM", "LEASE_RECOVERY",
                    ids.nextId(), json.canonicalJson(Map.of(
                    "deliveryId", delivery.deliveryId().value(), "recovery", recovery)), occurredAt));
            workspaceSequence = nextSequence;
        }
        return new RecoveryResult(requeued, retried, outcomeUnknown);
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
            throw new IllegalStateException("过期 Delivery TaskPackage 无法生成 retry", exception);
        }
    }

    public record RecoveryResult(int requeued, int retried, int outcomeUnknown) { }
    private record RetryPackage(String json, String hash) { }
}
