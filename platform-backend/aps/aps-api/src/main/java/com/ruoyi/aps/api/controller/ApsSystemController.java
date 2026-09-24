package com.ruoyi.aps.api.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.ruoyi.common.annotation.Anonymous;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APS 基础健康与真实能力声明。
 */
@RestController
@RequestMapping("/api/aps/v1")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
public class ApsSystemController
{
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private final Clock clock;
    private final boolean persistenceEnabled;
    private final boolean solverReady;
    private final boolean reportingEnabled;

    @Autowired
    public ApsSystemController(
            @Value("${aps.persistence.enabled:false}") boolean persistenceEnabled,
            @Value("${aps.solver.enabled:false}") boolean solverEnabled,
            @Value("${aps.worker.enabled:false}") boolean workerEnabled,
            @Value("${aps.worker.polling-enabled:false}") boolean pollingEnabled,
            @Value("${aps.reporting.enabled:false}") boolean reportingEnabled)
    {
        this(Clock.systemUTC(), persistenceEnabled,
                persistenceEnabled && solverEnabled && workerEnabled && pollingEnabled,
                persistenceEnabled && reportingEnabled);
    }

    ApsSystemController(Clock clock, boolean persistenceEnabled, boolean solverReady,
            boolean reportingEnabled)
    {
        this.clock = clock;
        this.persistenceEnabled = persistenceEnabled;
        this.solverReady = solverReady;
        this.reportingEnabled = reportingEnabled;
    }

    @Anonymous
    @GetMapping("/health")
    public HealthResponse health()
    {
        return new HealthResponse("1.0", "APS_HEALTH", "aps-api", "UP",
                UTC_MILLIS.format(Instant.now(clock)));
    }

    @PreAuthorize("@ss.hasPermi('aps:readiness:view')")
    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities()
    {
        return new CapabilitiesResponse(
                "1.0",
                "APS_CAPABILITIES",
                "aps-api",
                "1.0",
                "IMP_10_TECHNICAL_IN_PROGRESS",
                new ExecutionTopology("SINGLE_WORKER", false, false, false, false, false),
                List.of(
                        capability("RESOURCE_CALENDAR", persistenceEnabled),
                        capability("ROUTING_ORDER", persistenceEnabled),
                        new Capability("SCHEMA_METADATA", "1.0", "CONTRACT_ONLY", "CAPABILITY_NOT_IMPLEMENTED"),
                        new Capability("INPUT_VALIDATION", "1.0", "AVAILABLE", null),
                        capability("PLAN_REQUEST_STATUS", persistenceEnabled),
                        capability("SOLVER", solverReady),
                        capability("PUBLISH", persistenceEnabled),
                        capability("EXECUTION", persistenceEnabled),
                        capability("REPORTING", reportingEnabled),
                        new Capability("EXTERNAL_DISPATCH", "1.0", "DISABLED", "CAPABILITY_NOT_IMPLEMENTED")));
    }

    private Capability capability(String name, boolean enabled)
    {
        return enabled ? new Capability(name, "1.0", "AVAILABLE", null)
                : new Capability(name, "1.0", "DISABLED", "SERVICE_UNAVAILABLE");
    }

    public record HealthResponse(String schemaVersion, String contractType, String service, String status,
            String checkedAt)
    {
    }

    public record CapabilitiesResponse(String schemaVersion, String contractType, String service, String apiVersion,
            String implementationStage, ExecutionTopology topology, List<Capability> capabilities)
    {
    }

    public record ExecutionTopology(String workerMode, boolean leaseSupported, boolean fencingSupported,
            boolean inboxSupported, boolean outboxSupported, boolean externalDispatchSupported)
    {
    }

    public record Capability(String name, String version, String status, String reasonCode)
    {
    }
}
