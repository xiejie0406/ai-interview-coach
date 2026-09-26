package com.ruoyi.aps.api.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.application.planning.PlanWorkbenchService;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApsPlanWorkbenchControllerTest
{
    private static final Instant START = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void detailAndComparisonRequirePlanningViewPermission() throws Exception
    {
        assertPermission("detail", String.class);
        assertPermission("comparison", String.class, String.class);
        assertPermission("createLock", "@ss.hasPermi('aps:planning:lock')", String.class,
                ApsPlanWorkbenchController.CreateLockRequest.class);
        assertPermission("deleteLock", "@ss.hasPermi('aps:planning:lock')", String.class, String.class,
                long.class, long.class);
        assertPermission("createAdjustment", "@ss.hasPermi('aps:planning:adjust')", String.class,
                String.class, ApsPlanWorkbenchController.CreateAdjustmentRequest.class);
        assertPermission("createStructuralAdjustment", "@ss.hasPermi('aps:planning:adjust')", String.class,
                String.class, ApsPlanWorkbenchController.CreateStructuralAdjustmentRequest.class);
        assertPermission("publish", "@ss.hasPermi('aps:planning:publish')", String.class,
                ApsPlanWorkbenchController.PublishRequest.class);
        assertPermission("discard", "@ss.hasPermi('aps:planning:cancel')", String.class,
                ApsPlanWorkbenchController.DiscardCandidateRequest.class);
    }

    @Test
    void comparesJobsByStableMemberSetAndReportsTimeAndResourceChanges()
    {
        PlanRepository plans = mock(PlanRepository.class);
        ResourceManagementService resources = mock(ResourceManagementService.class);
        PlanWorkbenchService service = new PlanWorkbenchService(plans, resources);
        PlanRepository.PlanDetail base = detail("base", null, START, "machine-a");
        PlanRepository.PlanDetail target = detail("target", "base", START.plusSeconds(3600), "machine-b");
        when(plans.findDetail("base")).thenReturn(java.util.Optional.of(base));
        when(plans.findDetail("target")).thenReturn(java.util.Optional.of(target));

        PlanWorkbenchService.PlanComparison result = service.compare(
                new ResourceAccessScope("planner", true), "target", null);

        assertThat(result.baseVersionId()).isEqualTo("base");
        assertThat(result.summary()).isEqualTo(new PlanWorkbenchService.ChangeSummary(0, 0, 1, 0));
        assertThat(result.jobs()).singleElement().satisfies(change -> {
            assertThat(change.memberTaskIds()).containsExactly("task-a");
            assertThat(change.changeType()).isEqualTo("CHANGED");
            assertThat(change.dimensions()).containsExactly("TIME", "RESOURCE");
            assertThat(change.baseResourceIds()).containsExactly("machine-a");
            assertThat(change.targetResourceIds()).containsExactly("machine-b");
        });
    }

    private PlanRepository.PlanDetail detail(String id, String baseId, Instant start, String resourceId)
    {
        PlanRepository.PlanRecord version = new PlanRepository.PlanRecord(id, baseId, 1, id, "request-" + id,
                1, 1, "a".repeat(64), "FEASIBLE", START, START, 1);
        PlanRepository.PlanMember member = new PlanRepository.PlanMember("member-" + id, "task-a", 1,
                BigDecimal.TEN, "PCS");
        PlanRepository.PlanJob job = new PlanRepository.PlanJob("job-" + id, "op", "wc", "J-1", "NORMAL",
                null, BigDecimal.TEN, "PCS", null, null, null, start, start.plusSeconds(1800), List.of(member));
        PlanRepository.PlanSegment segment = new PlanRepository.PlanSegment("segment-" + id, job.id(), "phase", 1,
                "RUN", start, start.plusSeconds(1800), BigDecimal.TEN, null, null, "PCS");
        PlanRepository.PlanAllocation allocation = new PlanRepository.PlanAllocation("allocation-" + id,
                segment.id(), "phase", "requirement", resourceId, "MACHINE", 1, BigDecimal.ONE);
        return new PlanRepository.PlanDetail(version, null, "b".repeat(64), List.of(job), List.of(segment),
                List.of(allocation), List.of(), List.of(), null, null);
    }

    private void assertPermission(String method, Class<?>... parameterTypes) throws Exception
    {
        assertPermission(method, "@ss.hasPermi('aps:planning:view')", parameterTypes);
    }

    private void assertPermission(String method, String expected, Class<?>... parameterTypes) throws Exception
    {
        PreAuthorize authorization = ApsPlanWorkbenchController.class.getMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo(expected);
    }
}
