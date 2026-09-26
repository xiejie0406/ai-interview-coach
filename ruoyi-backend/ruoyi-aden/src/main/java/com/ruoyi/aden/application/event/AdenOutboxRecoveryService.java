package com.ruoyi.aden.application.event;

import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 将领取、成功确认和失败恢复拆成短事务，事务中不执行网络发送。 */
public class AdenOutboxRecoveryService {
    private final AdenOutboxRecoveryPort port;
    private final AdenIdGenerator idGenerator;
    private final Clock clock;
    private final Duration claimTtl;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;
    private final int maxAttempts;

    public AdenOutboxRecoveryService(AdenOutboxRecoveryPort port,
                                     AdenIdGenerator idGenerator,
                                     Clock clock,
                                     int claimTtlSeconds,
                                     int initialRetryDelayMilliseconds,
                                     int maxRetryDelayMilliseconds,
                                     int maxAttempts) {
        this.port = Objects.requireNonNull(port, "port");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.claimTtl = Duration.ofSeconds(claimTtlSeconds);
        this.initialRetryDelay = Duration.ofMillis(initialRetryDelayMilliseconds);
        this.maxRetryDelay = Duration.ofMillis(maxRetryDelayMilliseconds);
        if (claimTtlSeconds < 1 || initialRetryDelayMilliseconds < 1
                || maxRetryDelayMilliseconds < initialRetryDelayMilliseconds || maxAttempts < 1) {
            throw new IllegalArgumentException("Outbox 恢复参数非法");
        }
        this.maxAttempts = maxAttempts;
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public List<AdenOutboxMessage> claim(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Outbox batch 必须在 1..100");
        Instant now = clock.instant();
        return List.copyOf(port.claimReady(idGenerator.nextId(), now, now.plus(claimTtl), limit));
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public void acknowledge(AdenOutboxMessage message) {
        requireClaim(message);
        port.markPublished(message.workspaceId(), message.outboxId(), message.claimToken(), clock.instant());
    }

    @Transactional(transactionManager = "adenTransactionManager")
    public void fail(AdenOutboxMessage message, Throwable failure) {
        requireClaim(message);
        Instant now = clock.instant();
        String summary = errorSummary(failure);
        if (message.attempts() >= maxAttempts) {
            port.markDead(message.workspaceId(), message.outboxId(), message.claimToken(), summary, now);
            return;
        }
        port.markRetry(message.workspaceId(), message.outboxId(), message.claimToken(), summary,
                now.plus(retryDelay(message.attempts())), now);
    }

    Duration retryDelay(int completedAttempts) {
        long multiplier = 1L << Math.min(Math.max(completedAttempts - 1, 0), 30);
        long delay;
        try {
            delay = Math.multiplyExact(initialRetryDelay.toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            delay = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(delay, maxRetryDelay.toMillis()));
    }

    private static void requireClaim(AdenOutboxMessage message) {
        Objects.requireNonNull(message, "message");
        if (message.claimToken().isBlank()) throw new IllegalArgumentException("claimToken 不能为空");
    }

    private static String errorSummary(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message.trim());
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
