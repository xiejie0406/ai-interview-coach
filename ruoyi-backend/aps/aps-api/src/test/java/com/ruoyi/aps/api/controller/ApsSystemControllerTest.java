package com.ruoyi.aps.api.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ApsSystemControllerTest
{
    @Test
    void reportsOnlyActuallyImplementedCapabilities() throws Exception
    {
        ApsSystemController controller = new ApsSystemController(
                Clock.fixed(Instant.parse("2026-09-13T01:02:03.456Z"), ZoneOffset.UTC),
                true, true, true);

        ApsSystemController.HealthResponse health = controller.health();
        ApsSystemController.CapabilitiesResponse capabilities = controller.capabilities();

        assertThat(health.checkedAt()).isEqualTo("2026-09-13T01:02:03.456Z");
        assertThat(capabilities.implementationStage()).isEqualTo("IMP_10_TECHNICAL_IN_PROGRESS");
        assertThat(capabilities.capabilities()).hasSize(10)
                .filteredOn(capability -> "AVAILABLE".equals(capability.status()))
                .extracting(ApsSystemController.Capability::name)
                .containsExactly("RESOURCE_CALENDAR", "ROUTING_ORDER", "INPUT_VALIDATION", "PLAN_REQUEST_STATUS",
                        "SOLVER", "PUBLISH", "EXECUTION", "REPORTING");
        assertThat(capabilities.topology().workerMode()).isEqualTo("SINGLE_WORKER");
        assertThat(capabilities.topology().leaseSupported()).isFalse();

        PreAuthorize authorization = ApsSystemController.class
                .getMethod("capabilities")
                .getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("@ss.hasPermi('aps:readiness:view')");
    }
}
