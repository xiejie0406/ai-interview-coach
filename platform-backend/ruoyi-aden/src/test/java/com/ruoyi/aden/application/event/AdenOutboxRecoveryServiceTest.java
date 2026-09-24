package com.ruoyi.aden.application.event;

import com.ruoyi.aden.domain.event.AdenOutboxState;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenOutboxRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T03:00:00Z");

    @Test
    void claimsWithoutPublishingInsideTransactionAndAcknowledgesByFenceToken() {
        FakePort port = new FakePort();
        AdenOutboxRecoveryService service = service(port, 4);

        List<AdenOutboxMessage> claimed = service.claim(8);

        assertEquals(1, claimed.size());
        assertEquals("claim-token", port.claimToken);
        assertEquals(NOW.plusSeconds(30), port.claimedUntil);
        assertEquals(0, port.publishCalls);

        service.acknowledge(claimed.get(0));
        assertEquals(1, port.publishCalls);
        assertEquals("claim-token", port.settleToken);
    }

    @Test
    void retriesExponentiallyThenMovesToDeadAtBoundedAttempt() {
        FakePort port = new FakePort();
        AdenOutboxRecoveryService service = service(port, 3);

        service.fail(message(1), new IllegalStateException("temporary"));
        assertEquals(NOW.plusMillis(100), port.nextAvailableAt);
        service.fail(message(2), new IllegalStateException("temporary"));
        assertEquals(NOW.plusMillis(200), port.nextAvailableAt);
        service.fail(message(3), new IllegalStateException("terminal"));

        assertEquals(2, port.retryCalls);
        assertEquals(1, port.deadCalls);
        assertEquals("IllegalStateException: terminal", port.error);
        assertEquals(Duration.ofMillis(800), service.retryDelay(8));
    }

    @Test
    void rejectsUnboundedBatch() {
        assertThrows(IllegalArgumentException.class, () -> service(new FakePort(), 3).claim(101));
    }

    private static AdenOutboxRecoveryService service(FakePort port, int maxAttempts) {
        return new AdenOutboxRecoveryService(port, () -> "claim-token",
                Clock.fixed(NOW, ZoneOffset.UTC), 30, 100, 800, maxAttempts);
    }

    private static AdenOutboxMessage message(int attempts) {
        return new AdenOutboxMessage(
                new AdenWorkspaceId("11111111-1111-4111-8111-111111111111"),
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333", 1,
                "TASK_CREATED", "{}", "44444444-4444-4444-8444-444444444444",
                "SSE", AdenOutboxState.CLAIMED, attempts, "claim-token", NOW.plusSeconds(30));
    }

    private static final class FakePort implements AdenOutboxRecoveryPort {
        private String claimToken;
        private Instant claimedUntil;
        private String settleToken;
        private Instant nextAvailableAt;
        private String error;
        private int publishCalls;
        private int retryCalls;
        private int deadCalls;

        @Override
        public List<AdenOutboxMessage> claimReady(String token, Instant now, Instant until, int limit) {
            claimToken = token;
            claimedUntil = until;
            return new ArrayList<>(List.of(message(1)));
        }

        @Override
        public void markPublished(AdenWorkspaceId workspaceId, String outboxId,
                                  String token, Instant publishedAt) {
            publishCalls++;
            settleToken = token;
        }

        @Override
        public void markRetry(AdenWorkspaceId workspaceId, String outboxId, String token,
                              String value, Instant availableAt, Instant updatedAt) {
            retryCalls++;
            settleToken = token;
            error = value;
            nextAvailableAt = availableAt;
        }

        @Override
        public void markDead(AdenWorkspaceId workspaceId, String outboxId, String token,
                             String value, Instant updatedAt) {
            deadCalls++;
            settleToken = token;
            error = value;
        }
    }
}
