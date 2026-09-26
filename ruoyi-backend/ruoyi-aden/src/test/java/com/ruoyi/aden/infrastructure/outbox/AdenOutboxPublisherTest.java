package com.ruoyi.aden.infrastructure.outbox;

import com.ruoyi.aden.application.event.AdenOutboxMessage;
import com.ruoyi.aden.application.event.AdenOutboxRecoveryService;
import com.ruoyi.aden.domain.event.AdenOutboxState;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.stream.AdenWorkspaceEventBroadcaster;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdenOutboxPublisherTest {
    @Test
    void publishOnlyWakesWorkspaceThenAcknowledgesClaim() {
        AdenOutboxRecoveryService recovery = mock(AdenOutboxRecoveryService.class);
        AdenWorkspaceEventBroadcaster broadcaster = mock(AdenWorkspaceEventBroadcaster.class);
        AdenOutboxMessage message = message();
        when(recovery.claim(10)).thenReturn(List.of(message));

        int claimed = new AdenOutboxPublisher(recovery, broadcaster).publishOnce(10);

        assertEquals(1, claimed);
        verify(broadcaster).wake(message.workspaceId());
        verify(recovery).acknowledge(message);
        verify(recovery, never()).fail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishFailureReturnsClaimToBoundedRecoveryPath() {
        AdenOutboxRecoveryService recovery = mock(AdenOutboxRecoveryService.class);
        AdenWorkspaceEventBroadcaster broadcaster = mock(AdenWorkspaceEventBroadcaster.class);
        AdenOutboxMessage message = message();
        IllegalStateException failure = new IllegalStateException("synthetic publish failure");
        when(recovery.claim(10)).thenReturn(List.of(message));
        doThrow(failure).when(broadcaster).wake(message.workspaceId());

        int claimed = new AdenOutboxPublisher(recovery, broadcaster).publishOnce(10);

        assertEquals(1, claimed);
        verify(recovery).fail(message, failure);
        verify(recovery, never()).acknowledge(message);
    }

    private static AdenOutboxMessage message() {
        return new AdenOutboxMessage(
                new AdenWorkspaceId("11111111-1111-4111-8111-111111111111"),
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", 1,
                "aden.task.created.v1", "{\"to\":\"DRAFT\"}",
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "SSE",
                AdenOutboxState.CLAIMED, 1,
                "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                Instant.parse("2026-09-12T12:01:00Z"));
    }
}
