package com.ruoyi.aps.api.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.application.planning.PlanRequestService;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 轮询 MySQL 最终事实并只推送状态变化；断线不会改变求解或计划状态。 */
@Component
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
public final class ApsPlanProgressStream
{
    private static final Set<String> TERMINAL = Set.of(
            "FEASIBLE", "CONFLICT", "CANCELLED", "PUBLISHED", "FAILED", "SUPERSEDED");
    private final PlanRequestService service;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aps-plan-progress-sse");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public ApsPlanProgressStream(PlanRequestService service) { this.service = service; }

    public SseEmitter stream(ResourceAccessScope access, String requestId)
    {
        service.status(access, requestId);
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        emitters.add(emitter);
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        AtomicReference<String> lastFingerprint = new AtomicReference<>();
        Runnable close = () -> {
            emitters.remove(emitter);
            ScheduledFuture<?> scheduled = future.get();
            if (scheduled != null) scheduled.cancel(false);
        };
        emitter.onCompletion(close);
        emitter.onTimeout(() -> { close.run(); emitter.complete(); });
        emitter.onError(error -> close.run());
        Runnable poll = () -> {
            try
            {
                PlanRepository.PlanRequestSnapshot snapshot = service.status(access, requestId);
                ProgressEvent event = event(snapshot);
                String fingerprint = event.planStatus() + ':' + snapshot.plan().rowVersion();
                if (!fingerprint.equals(lastFingerprint.getAndSet(fingerprint)))
                    emitter.send(SseEmitter.event().id(Long.toString(snapshot.plan().rowVersion()))
                            .name("plan-status").data(event));
                if (TERMINAL.contains(event.planStatus())) { close.run(); emitter.complete(); }
            }
            catch (Exception exception)
            {
                close.run();
                emitter.completeWithError(exception);
            }
        };
        future.set(scheduler.scheduleWithFixedDelay(poll, 0, 1, TimeUnit.SECONDS));
        return emitter;
    }

    private ProgressEvent event(PlanRepository.PlanRequestSnapshot snapshot)
    {
        return new ProgressEvent("1.0", "PLAN_REQUEST_PROGRESS", snapshot.plan().requestId(), snapshot.plan().id(),
                snapshot.plan().status(), snapshot.solverStatus() == null ? null : snapshot.solverStatus().name(),
                snapshot.resultKind() == null ? null : snapshot.resultKind().name(), snapshot.reasonCodes(),
                snapshot.plan().updatedAt());
    }

    @PreDestroy
    public void close()
    {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
        scheduler.shutdownNow();
    }

    public record ProgressEvent(String schemaVersion, String contractType, String requestId, String planVersionId,
            String planStatus, String solverStatus, String resultKind, List<String> reasonCodes, Instant updatedAt) { }
}
