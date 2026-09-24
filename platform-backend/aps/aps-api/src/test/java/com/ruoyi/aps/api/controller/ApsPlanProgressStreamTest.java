package com.ruoyi.aps.api.controller;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.application.planning.PlanRequestService;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.solver.contract.SolverResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApsPlanProgressStreamTest
{
    @Test
    void pollsFinalDatabaseStateAndStopsAfterTerminalStatus() throws Exception
    {
        PlanRequestService service = mock(PlanRequestService.class);
        String requestId = "00000000-0000-4000-8000-000000000001";
        ResourceAccessScope access = new ResourceAccessScope("100", true);
        CountDownLatch terminalRead = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        when(service.status(any(ResourceAccessScope.class), eq(requestId))).thenAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) return snapshot(requestId, "SOLVING", 1,
                    null, null);
            terminalRead.countDown();
            return snapshot(requestId, "FEASIBLE", 2, SolverResult.SolverStatus.OPTIMAL,
                    SolverResult.ResultKind.FEASIBLE);
        });

        ApsPlanProgressStream stream = new ApsPlanProgressStream(service);
        try
        {
            assertThat(stream.stream(access, requestId)).isNotNull();
            assertThat(terminalRead.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(reads).hasValue(2);
        }
        finally
        {
            stream.close();
        }
    }

    private PlanRepository.PlanRequestSnapshot snapshot(String requestId, String status, long rowVersion,
            SolverResult.SolverStatus solverStatus, SolverResult.ResultKind resultKind)
    {
        Instant at = Instant.parse("2026-09-14T00:00:00Z");
        PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord(
                "00000000-0000-4000-8000-000000000002", null, 1, "候选计划", requestId,
                1, 1, "0".repeat(64), status, at, at, rowVersion);
        return new PlanRepository.PlanRequestSnapshot(plan, null, solverStatus, resultKind, List.of());
    }
}
