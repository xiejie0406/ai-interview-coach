package com.ruoyi.aden.infrastructure.stream;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdenWorkspaceEventBroadcasterTest {
    private static final AdenWorkspaceId WORKSPACE =
            new AdenWorkspaceId("11111111-1111-4111-8111-111111111111");
    private static final AdenOperatorPrincipal PRINCIPAL =
            new AdenOperatorPrincipal(42, "operator", Set.of("aden:event:subscribe"));

    private ThreadPoolTaskExecutor drainExecutor;
    private ThreadPoolTaskExecutor sendExecutor;

    @BeforeEach
    void startExecutors() {
        drainExecutor = executor("test-aden-drain-", 1, 16);
        sendExecutor = executor("test-aden-send-", 1, 16);
    }

    @AfterEach
    void stopExecutors() {
        drainExecutor.shutdown();
        sendExecutor.shutdown();
    }

    @Test
    void replaysLedgerEventsInOrderAndKeepsConnectionRegistered() throws Exception {
        assertEquals("aden_event", AdenWorkspaceEventBroadcaster.SSE_EVENT_NAME);
        AdenOperatorQueryService queries = mock(AdenOperatorQueryService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        var first = event("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "1");
        var second = event("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "2");
        when(queries.events(any(), any(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(new AdenOperatorQueryService.EventBatch(List.of(first, second), null, 2));
        when(queries.streamCursor(WORKSPACE, 1)).thenReturn("aden-c1.cursor-1");
        when(queries.streamCursor(WORKSPACE, 2)).thenReturn("aden-c1.cursor-2");
        AtomicInteger sends = new AtomicInteger();
        doAnswer(invocation -> { sends.incrementAndGet(); return null; })
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        var broadcaster = new AdenWorkspaceEventBroadcaster(
                queries, drainExecutor, sendExecutor, 100, 4, 1, 5, timeout -> emitter);
        broadcaster.open(PRINCIPAL, WORKSPACE, null, "cccccccc-cccc-4ccc-8ccc-cccccccccccc");

        await().atMost(2, TimeUnit.SECONDS).until(() -> sends.get() == 2);
        assertEquals(1, broadcaster.connectionCount());
        verify(queries).streamCursor(WORKSPACE, 1);
        verify(queries).streamCursor(WORKSPACE, 2);
    }

    @Test
    void slowConsumerOverflowClosesConnectionInsteadOfDroppingLedgerEvents() throws Exception {
        AdenOperatorQueryService queries = mock(AdenOperatorQueryService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(queries.events(any(), any(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(new AdenOperatorQueryService.EventBatch(List.of(event(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "1")), null, 1));
        when(queries.streamCursor(WORKSPACE, 1)).thenReturn("aden-c1.cursor-1");
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        var broadcaster = new AdenWorkspaceEventBroadcaster(
                queries, drainExecutor, sendExecutor, 100, 1, 1, 5, timeout -> emitter);
        broadcaster.open(PRINCIPAL, WORKSPACE, null, "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        assertTrue(sending.await(1, TimeUnit.SECONDS));

        broadcaster.wake(WORKSPACE);
        broadcaster.wake(WORKSPACE);

        await().atMost(1, TimeUnit.SECONDS).until(() -> broadcaster.connectionCount() == 0);
        verify(emitter, times(1)).completeWithError(any(AdenApplicationException.class));
        release.countDown();
    }

    @Test
    void blockedSendIsInterruptedAtConfiguredTimeoutAndConnectionIsCleaned() throws Exception {
        AdenOperatorQueryService queries = mock(AdenOperatorQueryService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(queries.events(any(), any(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(new AdenOperatorQueryService.EventBatch(List.of(event(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "1")), null, 1));
        when(queries.streamCursor(WORKSPACE, 1)).thenReturn("aden-c1.cursor-1");
        CountDownLatch sending = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            new CountDownLatch(1).await();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var broadcaster = new AdenWorkspaceEventBroadcaster(
                queries, drainExecutor, sendExecutor, 100, 4, 1, 5, timeout -> emitter);

        broadcaster.open(PRINCIPAL, WORKSPACE, null, "cccccccc-cccc-4ccc-8ccc-cccccccccccc");

        assertTrue(sending.await(1, TimeUnit.SECONDS));
        await().atMost(2, TimeUnit.SECONDS).until(() -> broadcaster.connectionCount() == 0);
        verify(emitter).completeWithError(any(AdenApplicationException.class));
    }

    @Test
    void fifthConnectionForSameUserIsRejectedWithStableRateLimitCode() {
        AdenOperatorQueryService queries = mock(AdenOperatorQueryService.class);
        when(queries.events(any(), any(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(new AdenOperatorQueryService.EventBatch(List.of(), null, 0));
        var broadcaster = new AdenWorkspaceEventBroadcaster(
                queries, drainExecutor, sendExecutor, 100, 4, 1, 5, SseEmitter::new);
        for (int index = 0; index < 4; index++) {
            broadcaster.open(PRINCIPAL, WORKSPACE, null,
                    "cccccccc-cccc-4ccc-8ccc-" + String.format("%012d", index));
        }
        assertEquals(4, broadcaster.connectionCount());

        AdenApplicationException failure = assertThrows(AdenApplicationException.class,
                () -> broadcaster.open(PRINCIPAL, WORKSPACE, null,
                        "dddddddd-dddd-4ddd-8ddd-dddddddddddd"));

        assertEquals("ADEN_RATE_LIMITED", failure.errorCode());
    }

    private static AdenOperatorQueryService.EventEnvelope event(String eventId, String sequence) {
        return new AdenOperatorQueryService.EventEnvelope(1, eventId, WORKSPACE.value(),
                "aden.task.created.v1", "TASK", "22222222-2222-4222-8222-222222222222",
                "1", sequence, Instant.parse("2026-09-12T12:00:00Z"),
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc", Map.of("to", "DRAFT"));
    }

    private static ThreadPoolTaskExecutor executor(String prefix, int threads, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queue);
        executor.initialize();
        return executor;
    }
}
