package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 高频心跳只维护 Session/Delivery 租约，不发布 Event、Outbox 或 Audit。 */
public class AdenRunnerHeartbeatService {
    private final AdenRunnerDeliveryRepository repository;
    private final int sessionTtlSeconds;
    private final int leaseTtlSeconds;

    public AdenRunnerHeartbeatService(AdenRunnerDeliveryRepository repository,
                                      int sessionTtlSeconds,
                                      int leaseTtlSeconds) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (sessionTtlSeconds < 1 || leaseTtlSeconds < 1) {
            throw new IllegalArgumentException("heartbeat TTL 必须为正数");
        }
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.leaseTtlSeconds = leaseTtlSeconds;
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public HeartbeatResult heartbeat(HeartbeatCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.sessionHeartbeatSequence() < 1) {
            throw new IllegalArgumentException("sessionHeartbeatSequence 必须为正数");
        }
        validatePulses(command.deliveries());
        var session = lockValidSession(command.principal());
        try {
            repository.heartbeatSession(session, command.sessionHeartbeatSequence(), sessionTtlSeconds);
        } catch (IllegalStateException conflict) {
            throw new AdenApplicationException("ADEN_HEARTBEAT_OUT_OF_ORDER",
                    "Session heartbeat sequence 必须严格递增");
        }

        List<AdenRunnerDeliveryRepository.HeartbeatDelivery> refreshed = command.deliveries().stream()
                .sorted(Comparator.comparing(pulse -> pulse.deliveryId().value()))
                .map(pulse -> repository.heartbeatDelivery(command.principal(), pulse.deliveryId(),
                                pulse.fenceToken(), pulse.heartbeatSequence(), leaseTtlSeconds)
                        .orElseThrow(() -> new AdenApplicationException("ADEN_RUNNER_LEASE_LOST",
                                "Delivery lease、fence 或 heartbeat sequence 已失效")))
                .toList();
        List<AdenDeliveryId> activeIds = command.deliveries().stream()
                .map(DeliveryPulse::deliveryId).toList();
        return new HeartbeatResult(command.sessionHeartbeatSequence(), refreshed,
                session.sessionId().value(), session.epoch(),
                session.databaseNow().plusSeconds(sessionTtlSeconds), session.databaseNow(),
                repository.findCancelRequested(command.principal(), activeIds));
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public HeartbeatResult heartbeatSession(SessionHeartbeatCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.sessionEpoch() != command.principal().sessionEpoch()
                || command.heartbeatSequence() < 1 || command.activeDeliveryIds() == null
                || command.activeDeliveryIds().size() > 32
                || new HashSet<>(command.activeDeliveryIds()).size() != command.activeDeliveryIds().size()) {
            throw new IllegalArgumentException("Session heartbeat 参数无效");
        }
        var session = lockValidSession(command.principal());
        try {
            repository.heartbeatSession(session, command.heartbeatSequence(), sessionTtlSeconds);
        } catch (IllegalStateException conflict) {
            throw new AdenApplicationException("ADEN_HEARTBEAT_OUT_OF_ORDER",
                    "Session heartbeat sequence 必须严格递增");
        }
        return new HeartbeatResult(command.heartbeatSequence(), List.of(),
                session.sessionId().value(), session.epoch(),
                session.databaseNow().plusSeconds(sessionTtlSeconds), session.databaseNow(),
                repository.findCancelRequested(command.principal(), command.activeDeliveryIds()));
    }

    @Transactional(transactionManager = "adenTransactionManager", isolation = Isolation.READ_COMMITTED)
    public DeliveryHeartbeatResult heartbeatDelivery(DeliveryHeartbeatCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.sessionEpoch() != command.principal().sessionEpoch()
                || command.fenceToken() < 1 || command.heartbeatSequence() < 1) {
            throw new IllegalArgumentException("Delivery heartbeat 参数无效");
        }
        lockValidSession(command.principal());
        var refreshed = repository.heartbeatDelivery(command.principal(), command.deliveryId(),
                        command.fenceToken(), command.heartbeatSequence(), leaseTtlSeconds)
                .orElseThrow(() -> new AdenApplicationException("ADEN_RUNNER_LEASE_LOST",
                        "Delivery lease、fence 或 heartbeat sequence 已失效"));
        return new DeliveryHeartbeatResult(refreshed.deliveryId(), refreshed.state(),
                refreshed.fenceToken(), refreshed.leaseUntil(), refreshed.cancelRequested(),
                refreshed.databaseNow());
    }

    private AdenRunnerDeliveryRepository.SessionCapacity lockValidSession(
            AdenRunnerSessionPrincipal principal) {
        var session = repository.lockSession(principal).orElseThrow(this::invalidSession);
        if (session.runnerCurrentEpoch() != principal.sessionEpoch()
                || session.epoch() != principal.sessionEpoch()) {
            throw new AdenApplicationException("ADEN_SESSION_EPOCH_STALE",
                    "Runner principal 已被更新的 Session epoch 取代");
        }
        if (!"ACTIVE".equals(session.status()) || !session.expiresAt().isAfter(session.databaseNow())) {
            throw invalidSession();
        }
        return session;
    }

    private void validatePulses(List<DeliveryPulse> deliveries) {
        if (deliveries == null || deliveries.size() > 32) {
            throw new IllegalArgumentException("单次 heartbeat Delivery 数必须在 0..32 范围内");
        }
        Set<AdenDeliveryId> unique = new HashSet<>();
        for (DeliveryPulse pulse : deliveries) {
            Objects.requireNonNull(pulse, "delivery pulse");
            if (!unique.add(pulse.deliveryId()) || pulse.fenceToken() < 1
                    || pulse.heartbeatSequence() < 1) {
                throw new IllegalArgumentException("Delivery heartbeat 标识、fence 或 sequence 非法");
            }
        }
    }

    private AdenApplicationException invalidSession() {
        return new AdenApplicationException("ADEN_RUNNER_SESSION_INVALID", "Runner session 无效或已过期");
    }

    public record HeartbeatCommand(AdenRunnerSessionPrincipal principal,
                                   long sessionHeartbeatSequence,
                                   List<DeliveryPulse> deliveries) {
        public HeartbeatCommand {
            Objects.requireNonNull(principal, "principal");
            deliveries = deliveries == null ? null : List.copyOf(deliveries);
        }
    }

    public record DeliveryPulse(AdenDeliveryId deliveryId, long fenceToken,
                                long heartbeatSequence) {
        public DeliveryPulse { Objects.requireNonNull(deliveryId, "deliveryId"); }
    }

    public record SessionHeartbeatCommand(AdenRunnerSessionPrincipal principal,
                                          long sessionEpoch,
                                          long heartbeatSequence,
                                          Instant observedAt,
                                          List<AdenDeliveryId> activeDeliveryIds) {
        public SessionHeartbeatCommand {
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(observedAt, "observedAt");
            activeDeliveryIds = activeDeliveryIds == null ? null : List.copyOf(activeDeliveryIds);
        }
    }

    public record DeliveryHeartbeatCommand(AdenRunnerSessionPrincipal principal,
                                           AdenDeliveryId deliveryId,
                                           long sessionEpoch,
                                           long fenceToken,
                                           long heartbeatSequence,
                                           Instant observedAt) {
        public DeliveryHeartbeatCommand {
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(deliveryId, "deliveryId");
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    public record HeartbeatResult(long sessionHeartbeatSequence,
                                  List<AdenRunnerDeliveryRepository.HeartbeatDelivery> deliveries,
                                  String sessionId, long sessionEpoch,
                                  Instant sessionExpiresAt, Instant serverTime,
                                  List<AdenDeliveryId> cancelDeliveryIds) {
        public HeartbeatResult {
            deliveries = List.copyOf(deliveries);
            cancelDeliveryIds = List.copyOf(cancelDeliveryIds);
        }
    }

    public record DeliveryHeartbeatResult(AdenDeliveryId deliveryId, String state,
                                          long fenceToken, Instant leaseUntil,
                                          boolean cancelRequested, Instant serverTime) { }
}
