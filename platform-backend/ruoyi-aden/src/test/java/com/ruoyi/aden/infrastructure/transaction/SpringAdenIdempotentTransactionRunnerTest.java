package com.ruoyi.aden.infrastructure.transaction;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringAdenIdempotentTransactionRunnerTest {
    @Test
    void retriesTheWholeTransactionAndUsesControllableBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> waits = new ArrayList<>();
        SpringAdenIdempotentTransactionRunner runner = runner(3, waits);

        String result = runner.execute(new AdenIdempotencyKey("retry:key"), () -> {
            if (calls.incrementAndGet() < 3) throw new CannotAcquireLockException("deadlock");
            return "committed";
        });

        assertEquals("committed", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(20)), waits);
    }

    @Test
    void stopsAtBoundAndNeverRetriesNonTransientFailure() {
        AtomicInteger deadlockCalls = new AtomicInteger();
        SpringAdenIdempotentTransactionRunner runner = runner(3, new ArrayList<>());
        assertThrows(CannotAcquireLockException.class, () -> runner.execute(
                new AdenIdempotencyKey("bounded:key"), () -> {
                    deadlockCalls.incrementAndGet();
                    throw new CannotAcquireLockException("still deadlocked");
                }));
        assertEquals(3, deadlockCalls.get());

        AtomicInteger invalidCalls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> runner.execute(
                new AdenIdempotencyKey("invalid:key"), () -> {
                    invalidCalls.incrementAndGet();
                    throw new IllegalArgumentException("not retryable");
                }));
        assertEquals(1, invalidCalls.get());
    }

    private static SpringAdenIdempotentTransactionRunner runner(
            int maxAttempts, List<Duration> waits) {
        return new SpringAdenIdempotentTransactionRunner(
                new TransactionTemplate(new NoOpTransactionManager()),
                maxAttempts, Duration.ofMillis(10), Duration.ofMillis(100), waits::add);
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
