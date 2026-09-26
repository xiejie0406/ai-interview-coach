package com.ruoyi.aden.domain.runner;

import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskStepId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenRunnerDomainTest {
    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");
    private static final AdenWorkspaceId WORKSPACE = id(AdenWorkspaceId::new, 1);
    private static final AdenRunnerId RUNNER = id(AdenRunnerId::new, 2);
    private static final AdenCredentialId CREDENTIAL = id(AdenCredentialId::new, 3);
    private static final AdenSessionId SESSION = id(AdenSessionId::new, 4);
    private static final AdenDeliveryId DELIVERY = id(AdenDeliveryId::new, 5);

    @Test
    void capabilitySessionAndCredentialAreTimeAndEpochBound() {
        AdenCapabilitySnapshot capabilities = new AdenCapabilitySnapshot(Set.of(AdenCapabilityCode.CORE), NOW);
        AdenRunnerCredential credential = new AdenRunnerCredential(
                WORKSPACE, CREDENTIAL, RUNNER, 1, "a".repeat(64), "pepper-v1",
                AdenCredentialStatus.ACTIVE, NOW, NOW.plusSeconds(60));
        AdenRunnerSession session = session();

        assertTrue(capabilities.supports(AdenCapabilityCode.CORE));
        assertTrue(credential.activeAt(NOW.plusSeconds(59)));
        assertFalse(credential.activeAt(NOW.plusSeconds(60)));
        assertTrue(session.activeAt(NOW.plusSeconds(59), 1));
        assertFalse(session.activeAt(NOW.plusSeconds(59), 2));
        assertEquals(1, session.remainingCapacity());
    }

    @Test
    void claimIncrementsFenceAndStaleOrOutOfOrderReceiptCannotAdvance() {
        AdenRunnerDelivery claimed = ready().claim(session(), NOW.plusSeconds(60));
        assertEquals(AdenDeliveryState.LEASED, claimed.state());
        assertEquals(1, claimed.fencingToken().value());
        AdenRunnerReceipt started = receipt(claimed, 1, AdenReceiptType.STARTED);
        AdenRunnerDelivery running = claimed.apply(started, NOW.plusSeconds(1));
        assertEquals(AdenDeliveryState.RUNNING, running.state());

        assertThrows(IllegalStateException.class, () -> running.apply(
                new AdenRunnerReceipt(DELIVERY, SESSION, 1, new AdenFencingToken(0), 2,
                        AdenReceiptType.COMPLETED, "{}", NOW.plusSeconds(2)), NOW.plusSeconds(2)));
        assertThrows(IllegalStateException.class, () -> running.apply(
                receipt(running, 3, AdenReceiptType.COMPLETED), NOW.plusSeconds(2)));
        assertEquals(AdenDeliveryState.COMPLETED,
                running.apply(receipt(running, 2, AdenReceiptType.COMPLETED), NOW.plusSeconds(2)).state());
    }

    @Test
    void canceledTerminalRequiresExplicitSafePointReceipt() {
        AdenRunnerDelivery claimed = ready().claim(session(), NOW.plusSeconds(60));
        assertThrows(IllegalStateException.class, () -> claimed.apply(
                receipt(claimed, 1, AdenReceiptType.CANCELED_SAFE_POINT), NOW.plusSeconds(1)));
        AdenRunnerDelivery cancelRequested = claimed.requestCancel(NOW.plusMillis(500));
        assertEquals(AdenDeliveryState.CANCELED, cancelRequested.apply(
                receipt(cancelRequested, 1, AdenReceiptType.CANCELED_SAFE_POINT), NOW.plusSeconds(1)).state());
    }

    private static AdenRunnerDelivery ready() {
        return new AdenRunnerDelivery(WORKSPACE, DELIVERY,
                id(AdenTaskId::new, 6), id(AdenTaskStepId::new, 7), AdenDeliveryState.READY,
                1, AdenCapabilityCode.CORE, 100, "{}", "b".repeat(64),
                new AdenFencingToken(0), null, 0, 0, null, 0);
    }

    private static AdenRunnerSession session() {
        return new AdenRunnerSession(WORKSPACE, SESSION, RUNNER, CREDENTIAL,
                AdenSessionStatus.ACTIVE, 1, 2, 1, 0, NOW, NOW.plusSeconds(60), 0);
    }

    private static AdenRunnerReceipt receipt(AdenRunnerDelivery delivery, long sequence, AdenReceiptType type) {
        return new AdenRunnerReceipt(DELIVERY, SESSION, 1, delivery.fencingToken(),
                sequence, type, "{}", NOW.plusSeconds(sequence));
    }

    private static <T> T id(java.util.function.Function<String, T> factory, int suffix) {
        return factory.apply(String.format("00000000-0000-4000-8000-%012d", suffix));
    }
}
