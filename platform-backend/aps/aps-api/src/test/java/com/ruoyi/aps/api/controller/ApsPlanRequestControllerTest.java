package com.ruoyi.aps.api.controller;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ApsPlanRequestControllerTest
{
    @Test
    void endpointsUseDistinctSolveViewAndCancelPermissions() throws Exception
    {
        assertPermission("create", "@ss.hasPermi('aps:planning:solve')", String.class,
                ApsPlanRequestController.PlanRequestInput.class);
        assertPermission("status", "@ss.hasPermi('aps:planning:view')", String.class);
        assertPermission("events", "@ss.hasPermi('aps:planning:view')", String.class);
        assertPermission("cancel", "@ss.hasPermi('aps:planning:cancel')", String.class);
    }

    @Test
    void mapsReviewedBaselineReferenceAndFreezeBoundaryIntoCompileRequest()
    {
        String baselineId = "00000000-0000-4000-8000-000000000095";
        var input = new ApsPlanRequestController.PlanRequestInput("SITE_01",
                List.of("00000000-0000-4000-8000-000000000092"),
                List.of("00000000-0000-4000-8000-000000000010"), "2026-09-15T00:00:00.000Z", 7, 3,
                new ApsPlanRequestController.HorizonInput("2026-09-15T00:00:00.000Z",
                        "2026-09-16T00:00:00.000Z", "2026-09-17T00:00:00.000Z",
                        "2026-09-15T00:00:00.000Z", 60, "Asia/Shanghai"),
                new ApsPlanRequestController.ParametersInput(30, 1, 1, 0, 0), List.of(),
                new ApsPlanRequestController.BaseVersionInput(baselineId, "a".repeat(64),
                        "2026-09-15T06:00:00.000Z"));

        var request = input.toCompileRequest("00000000-0000-4000-8000-000000000090",
                "00000000-0000-4000-8000-000000000091");

        assertThat(request.baseVersion().planVersionId()).isEqualTo(baselineId);
        assertThat(request.baseVersion().inputHash()).isEqualTo("a".repeat(64));
        assertThat(request.baseVersion().freezeEndAt().toString()).isEqualTo("2026-09-15T06:00:00Z");
    }

    private void assertPermission(String method, String expected, Class<?>... parameterTypes) throws Exception
    {
        PreAuthorize authorization = ApsPlanRequestController.class.getMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo(expected);
    }
}
