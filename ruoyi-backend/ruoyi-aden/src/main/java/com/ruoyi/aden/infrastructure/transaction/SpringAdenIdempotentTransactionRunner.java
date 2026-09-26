package com.ruoyi.aden.infrastructure.transaction;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.reliability.AdenIdempotentTransactionRunner;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** MySQL 死锁/锁超时只重放完整幂等事务，并严格限制次数。 */
public final class SpringAdenIdempotentTransactionRunner implements AdenIdempotentTransactionRunner {
    @FunctionalInterface
    public interface Waiter {
        void await(Duration delay) throws InterruptedException;
    }

    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Waiter waiter;

    public SpringAdenIdempotentTransactionRunner(TransactionTemplate transactionTemplate,
                                                  int maxAttempts,
                                                  Duration initialDelay,
                                                  Duration maxDelay,
                                                  Waiter waiter) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts 必须为正数");
        this.maxAttempts = maxAttempts;
        this.initialDelay = positive(initialDelay, "initialDelay");
        this.maxDelay = positive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay 不得小于 initialDelay");
        }
        this.waiter = Objects.requireNonNull(waiter, "waiter");
    }

    public static Waiter threadSleepWaiter() {
        return delay -> Thread.sleep(delay.toMillis());
    }

    @Override
    public <T> T execute(AdenIdempotencyKey idempotencyKey, Supplier<T> wholeCommand) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(wholeCommand, "wholeCommand");
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> wholeCommand.get());
            } catch (RuntimeException failure) {
                if (!isRetryable(failure) || attempt >= maxAttempts) throw failure;
                await(delay(attempt));
            }
        }
    }

    private Duration delay(int failedAttempt) {
        long multiplier = 1L << Math.min(Math.max(failedAttempt - 1, 0), 30);
        long millis;
        try {
            millis = Math.multiplyExact(initialDelay.toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            millis = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(millis, maxDelay.toMillis()));
    }

    private void await(Duration delay) {
        try {
            waiter.await(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待重试时线程被中断", interrupted);
        }
    }

    private static boolean isRetryable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException) return true;
            if (current instanceof SQLException sql
                    && (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205
                    || "40001".equals(sql.getSQLState()))) return true;
        }
        return false;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " 必须为正数");
        return value;
    }
}
