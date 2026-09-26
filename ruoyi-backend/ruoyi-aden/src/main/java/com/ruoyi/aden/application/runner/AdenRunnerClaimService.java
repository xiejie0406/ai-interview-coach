package com.ruoyi.aden.application.runner;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Runner claim 的唯一事务入口：Inbox → Session → Delivery，保证幂等批次与租约所有权。 */
public class AdenRunnerClaimService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final String ACTIVE = "ACTIVE";

    private final AdenRunnerDeliveryRepository repository;
    private final AdenRequestFingerprint fingerprint;
    private final ObjectMapper objectMapper;
    private final AdenIdGenerator ids;
    private final Clock clock;
    private final int leaseTtlSeconds;
    private final int maxBatchSize;
    private final int inProgressTtlSeconds;
    private final int retentionSeconds;

    public AdenRunnerClaimService(AdenRunnerDeliveryRepository repository,
                                  AdenRequestFingerprint fingerprint,
                                  ObjectMapper objectMapper,
                                  AdenIdGenerator ids,
                                  Clock clock,
                                  int leaseTtlSeconds,
                                  int maxBatchSize,
                                  int inProgressTtlSeconds,
                                  int retentionSeconds) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (leaseTtlSeconds < 1 || maxBatchSize < 1 || inProgressTtlSeconds < 1
                || retentionSeconds < inProgressTtlSeconds) {
            throw new IllegalArgumentException("Runner claim 配置无效");
        }
        this.leaseTtlSeconds = leaseTtlSeconds;
        this.maxBatchSize = maxBatchSize;
        this.inProgressTtlSeconds = inProgressTtlSeconds;
        this.retentionSeconds = retentionSeconds;
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public ClaimResult claim(ClaimCommand command) {
        Objects.requireNonNull(command, "command");
        validate(command);
        Instant applicationNow = clock.instant();
        String requestHash = fingerprint.hashValue(Map.of(
                "workspaceId", command.principal().workspaceId().value(),
                "runnerId", command.principal().runnerId().value(),
                "sessionId", command.principal().sessionId().value(),
                "sessionEpoch", Long.toString(command.principal().sessionEpoch()),
                "claimRequestId", command.claimRequestId(),
                "capabilities", command.capabilities().stream().map(Enum::name).sorted().toList(),
                "capacity", command.capacity(),
                "batchLimit", command.batchLimit()));

        repository.lockWorkspace(command.principal().workspaceId());
        var existing = repository.findClaimInboxForUpdate(command.principal().workspaceId(),
                command.principal().sessionId(), command.idempotencyKey());
        if (existing.isPresent()) {
            var inbox = existing.get();
            if (!inbox.requestHash().equals(requestHash)) throw new AdenIdempotencyKeyReusedException();
            if (COMPLETED.equals(inbox.state()) && inbox.responseJson() != null) {
                ClaimResult replay = decode(inbox.responseJson(), true);
                if (replay.items().stream().anyMatch(item -> !repository.isLive(item.delivery()))) {
                    throw new AdenApplicationException("ADEN_RUNNER_LEASE_LOST",
                            "原 claim 批次中的租约已失效，必须使用新的 claimRequestId");
                }
                return replay;
            }
            throw new AdenApplicationException("ADEN_DEPENDENCY_UNAVAILABLE", "相同 claim 请求仍在处理中");
        }

        String inboxId = ids.nextId();
        repository.insertClaimInbox(new AdenRunnerDeliveryRepository.ClaimInbox(
                command.principal().workspaceId(), inboxId, command.principal().sessionId(),
                command.idempotencyKey(), requestHash, IN_PROGRESS, null,
                applicationNow.plusSeconds(inProgressTtlSeconds),
                applicationNow.plusSeconds(retentionSeconds), applicationNow));

        AdenRunnerDeliveryRepository.SessionCapacity session = repository.lockSession(command.principal())
                .orElseThrow(this::invalidSession);
        requireCurrentSession(command, session);
        int claimCount = Math.min(Math.min(command.batchLimit(), maxBatchSize), session.remaining());
        List<AdenRunnerDeliveryRepository.ClaimedDelivery> claimed = new ArrayList<>();
        if (claimCount > 0) {
            for (AdenCapabilityCode capability : command.capabilities().stream()
                    .sorted(Comparator.comparing(Enum::name)).toList()) {
                int remaining = claimCount - claimed.size();
                if (remaining == 0) break;
                for (var candidate : repository.lockReady(command.principal().workspaceId(),
                        capability, remaining)) {
                    claimed.add(repository.claim(candidate, command.principal(), leaseTtlSeconds));
                }
            }
            if (!claimed.isEmpty()) repository.increaseInFlight(session, claimed.size());
        }

        List<ClaimItem> items = claimed.stream().map(ClaimItem::new).toList();
        ClaimResult result = new ClaimResult(command.claimRequestId(),
                command.principal().sessionId().value(), command.principal().sessionEpoch(),
                items, session.databaseNow(), false);
        repository.completeClaimInbox(command.principal().workspaceId(), inboxId, encode(result), applicationNow);
        return result;
    }

    private void validate(ClaimCommand command) {
        if (command.capacity() < 1 || command.capacity() > 32) {
            throw new IllegalArgumentException("capacity 必须在 1..32 范围内");
        }
        if (command.batchLimit() < 1 || command.batchLimit() > maxBatchSize) {
            throw new IllegalArgumentException("batchLimit 超出服务端允许范围");
        }
        if (command.capabilities() == null || command.capabilities().isEmpty()) {
            throw new IllegalArgumentException("capabilities 不得为空");
        }
        if (!command.capabilities().equals(Set.of(AdenCapabilityCode.CORE))) {
            throw new AdenApplicationException("ADEN_CAPABILITY_NOT_ENABLED",
                    "当前阶段只允许 CORE 合成能力");
        }
    }

    private void requireCurrentSession(ClaimCommand command,
                                       AdenRunnerDeliveryRepository.SessionCapacity session) {
        if (!ACTIVE.equals(session.status())
                || session.epoch() != command.principal().sessionEpoch()
                || session.runnerCurrentEpoch() != command.principal().sessionEpoch()
                || !session.sessionId().equals(command.principal().sessionId())
                || !session.workspaceId().equals(command.principal().workspaceId())
                || !session.expiresAt().isAfter(session.databaseNow())) {
            throw invalidSession();
        }
        if (command.capacity() != session.capacity()) {
            throw new AdenApplicationException("ADEN_RUNNER_CAPACITY_MISMATCH",
                    "claim capacity 必须与服务端会话容量一致");
        }
    }

    private AdenApplicationException invalidSession() {
        return new AdenApplicationException("ADEN_RUNNER_SESSION_INVALID", "Runner session 无效或已过期");
    }

    private String encode(ClaimResult result) {
        return fingerprint.canonicalJson(new ReplayPayload(result.claimRequestId(), result.sessionId(),
                result.sessionEpoch(), result.items().stream().map(item -> replay(item.delivery())).toList(),
                result.serverTime().toString()));
    }

    private ClaimResult decode(String value, boolean replayed) {
        try {
            ReplayPayload payload = objectMapper.readValue(value, ReplayPayload.class);
            return new ClaimResult(payload.claimRequestId(), payload.sessionId(), payload.sessionEpoch(),
                    payload.deliveries().stream().map(this::claimed).map(ClaimItem::new).toList(),
                    Instant.parse(payload.serverTime()), replayed);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Runner claim Inbox 回放结果无法解析", exception);
        }
    }

    private ReplayDelivery replay(AdenRunnerDeliveryRepository.ClaimedDelivery delivery) {
        return new ReplayDelivery(delivery.workspaceId().value(), delivery.deliveryId().value(),
                delivery.taskId(), delivery.stepId(), delivery.attemptNo(), delivery.fenceToken(),
                delivery.leaseUntil().toString(), delivery.taskPackageJson(), delivery.taskPackageHash(),
                delivery.sessionId().value(), delivery.sessionEpoch());
    }

    private AdenRunnerDeliveryRepository.ClaimedDelivery claimed(ReplayDelivery delivery) {
        return new AdenRunnerDeliveryRepository.ClaimedDelivery(
                new com.ruoyi.aden.domain.workspace.AdenWorkspaceId(delivery.workspaceId()),
                new com.ruoyi.aden.domain.runner.AdenDeliveryId(delivery.deliveryId()),
                delivery.taskId(), delivery.stepId(), delivery.attemptNo(), delivery.fenceToken(),
                Instant.parse(delivery.leaseUntil()), delivery.taskPackageJson(),
                delivery.taskPackageHash(),
                new com.ruoyi.aden.domain.runner.AdenSessionId(delivery.sessionId()),
                delivery.sessionEpoch());
    }

    public record ClaimCommand(AdenRunnerSessionPrincipal principal,
                               AdenIdempotencyKey idempotencyKey,
                               String claimRequestId,
                               Set<AdenCapabilityCode> capabilities,
                               int capacity,
                               int batchLimit) {
        public ClaimCommand {
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(claimRequestId, "claimRequestId");
            capabilities = capabilities == null ? null : Set.copyOf(capabilities);
        }

        public ClaimCommand(AdenRunnerSessionPrincipal principal,
                            AdenIdempotencyKey idempotencyKey,
                            Set<AdenCapabilityCode> capabilities,
                            int capacity,
                            int batchLimit) {
            this(principal, idempotencyKey, idempotencyKey.value(), capabilities, capacity, batchLimit);
        }
    }

    public record ClaimItem(AdenRunnerDeliveryRepository.ClaimedDelivery delivery) { }

    public record ClaimResult(String claimRequestId, String sessionId, long sessionEpoch,
                              List<ClaimItem> items, Instant serverTime, boolean replayed) {
        public ClaimResult {
            items = List.copyOf(items);
        }
    }

    private record ReplayPayload(String claimRequestId, String sessionId, long sessionEpoch,
                                 List<ReplayDelivery> deliveries, String serverTime) { }

    private record ReplayDelivery(String workspaceId, String deliveryId, String taskId,
                                  String stepId, int attemptNo, long fenceToken,
                                  String leaseUntil, String taskPackageJson,
                                  String taskPackageHash, String sessionId,
                                  long sessionEpoch) { }
}
