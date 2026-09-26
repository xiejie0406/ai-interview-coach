package com.ruoyi.aps.infrastructure.mysql.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import com.ruoyi.aps.application.execution.ExecutionLifecycleService;
import com.ruoyi.aps.application.execution.ProductionReportingService;
import com.ruoyi.aps.application.execution.ExecutionRepository;
import com.ruoyi.aps.application.execution.QuantityRepository;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QualityDecision;
import com.ruoyi.aps.application.execution.ExecutionCatalog.ReportType;
import com.ruoyi.aps.application.execution.ExecutionCatalog.OccupancyActivityType;
import com.ruoyi.aps.application.execution.ExecutionCatalog.QuantityOperation;
import com.ruoyi.aps.application.order.OrderCatalog.ComponentSpec;
import com.ruoyi.aps.application.order.OrderCatalog.DemandType;
import com.ruoyi.aps.application.order.OrderCatalog.LineDependencySpec;
import com.ruoyi.aps.application.order.OrderCatalog.LotType;
import com.ruoyi.aps.application.order.OrderCatalog.OrderLine;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionOrder;
import com.ruoyi.aps.application.order.OrderManagementService;
import com.ruoyi.aps.application.order.OrderRepository;
import com.ruoyi.aps.application.planning.SolverInputCompiler;
import com.ruoyi.aps.application.planning.SolverInputCompiler.BaseVersionRequest;
import com.ruoyi.aps.application.planning.SolverInputCompiler.CompileRequest;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.application.planning.PlanRequestService;
import com.ruoyi.aps.application.planning.PlanWorkbenchService;
import com.ruoyi.aps.application.planning.PlanLockService;
import com.ruoyi.aps.application.planning.PlanAdjustmentService;
import com.ruoyi.aps.application.planning.PlanPublishService;
import com.ruoyi.aps.application.planning.PlanCandidateLifecycleService;
import com.ruoyi.aps.application.planning.PlanStructuralAdjustmentService;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.reporting.ReportingService;
import com.ruoyi.aps.application.resource.ResourceCatalog.Availability;
import com.ruoyi.aps.application.resource.ResourceCatalog.Resource;
import com.ruoyi.aps.application.resource.ResourceCatalog.Skill;
import com.ruoyi.aps.application.resource.ResourceCatalog.WorkCenter;
import com.ruoyi.aps.application.resource.ResourceCatalog.Workshop;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import com.ruoyi.aps.application.routing.RoutingCatalog.DurationModel;
import com.ruoyi.aps.application.routing.RoutingCatalog.Item;
import com.ruoyi.aps.application.routing.RoutingCatalog.ItemType;
import com.ruoyi.aps.application.routing.RoutingCatalog.OperationMode;
import com.ruoyi.aps.application.routing.RoutingCatalog.OperationPhase;
import com.ruoyi.aps.application.routing.RoutingCatalog.OperationSpec;
import com.ruoyi.aps.application.routing.RoutingCatalog.PhaseType;
import com.ruoyi.aps.application.routing.RoutingCatalog.ResourceHoldPolicy;
import com.ruoyi.aps.application.routing.RoutingCatalog.ResourceRequirement;
import com.ruoyi.aps.application.routing.RoutingCatalog.RouteEdgeDraft;
import com.ruoyi.aps.application.routing.RoutingCatalog.RouteGraphDraft;
import com.ruoyi.aps.application.routing.RoutingCatalog.RouteNodeDraft;
import com.ruoyi.aps.application.routing.RoutingCatalog.RouteVersion;
import com.ruoyi.aps.application.routing.RoutingManagementService;
import com.ruoyi.aps.domain.resource.AvailabilityType;
import com.ruoyi.aps.domain.resource.ResourceType;
import com.ruoyi.aps.domain.routing.DependencyType;
import com.ruoyi.aps.domain.execution.ExecutionAction;
import com.ruoyi.aps.domain.quantity.ProductionReportQuantities;
import com.ruoyi.aps.infrastructure.mysql.configuration.ApsDataSourceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.configuration.ApsFlywayConfiguration;
import com.ruoyi.aps.infrastructure.mysql.resource.ApsResourcePersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.planning.ApsPlanningPersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.routing.ApsManufacturingPersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.execution.ApsExecutionPersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.reporting.ApsReportingPersistenceConfiguration;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverResult;
import com.ruoyi.aps.solver.ortools.CpSatFiniteCapacitySolver;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApsBaselineMigrationTest
{
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "aps_workshop", "aps_work_center", "aps_resource", "aps_resource_skill",
            "aps_resource_availability", "aps_item", "aps_operation_spec", "aps_operation_phase",
            "aps_resource_requirement", "aps_route_version", "aps_route_node", "aps_route_edge",
            "aps_order", "aps_order_line", "aps_production_lot", "aps_task", "aps_task_dependency",
            "aps_material_demand", "aps_plan_version", "aps_plan_job", "aps_plan_job_member",
            "aps_plan_segment", "aps_plan_allocation", "aps_plan_lock", "aps_execution_run",
            "aps_actual_occupancy", "aps_production_report", "aps_output_lot", "aps_quantity_event");

    @Test
    void migratesAndValidatesBaselineAgainstIsolatedMysql() throws Exception
    {
        String adminUrl = System.getenv("APS_TEST_MYSQL_ADMIN_URL");
        Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank(),
                "仅在设置 APS_TEST_MYSQL_ADMIN_URL 的隔离 MySQL 验证中运行");
        String username = envOrDefault("APS_TEST_MYSQL_USERNAME", "root");
        String password = envOrDefault("APS_TEST_MYSQL_PASSWORD", "");
        String schema = "aps_imp02_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        assertThat(schema).matches("aps_imp02_[0-9a-f]{12}");

        try (Connection admin = DriverManager.getConnection(withUtc(adminUrl), username, password);
                Statement statement = admin.createStatement())
        {
            statement.execute("CREATE DATABASE " + schema
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        String schemaUrl = schemaUrl(adminUrl, schema);
        try
        {
            Flyway flyway = Flyway.configure()
                    .dataSource(withUtc(schemaUrl), username, password)
                    .locations("classpath:db/migration/aps")
                    .table("aps_flyway_schema_history")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .validateMigrationNaming(true)
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            verifySpringWiring(schemaUrl, username, password);

            try (Connection connection = DriverManager.getConnection(withUtc(schemaUrl), username, password))
            {
                verifySchema(connection, schema);
                verifyConstraintsAndTransactions(connection);
            }
            verifyForwardOnlyUpgrade(schemaUrl, username, password);
        }
        finally
        {
            try (Connection admin = DriverManager.getConnection(withUtc(adminUrl), username, password);
                    Statement statement = admin.createStatement())
            {
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }

    private void verifyForwardOnlyUpgrade(String schemaUrl, String username, String password) throws SQLException
    {
        Flyway upgrade = Flyway.configure()
                .dataSource(withUtc(schemaUrl), username, password)
                .locations("classpath:db/migration/aps", "classpath:db/migration/aps-upgrade")
                .table("aps_flyway_schema_history")
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        upgrade.validate();
        try (Connection connection = DriverManager.getConnection(withUtc(schemaUrl), username, password))
        {
            assertThat(singleLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                            + "AND table_name = 'aps_upgrade_probe'",
                    connection.getCatalog())).isEqualTo(1);
        }
    }

    private void verifySpringWiring(String schemaUrl, String username, String password)
    {
        new ApplicationContextRunner()
                .withUserConfiguration(ApsDataSourceConfiguration.class, ApsFlywayConfiguration.class,
                        ApsResourcePersistenceConfiguration.class, ApsManufacturingPersistenceConfiguration.class,
                        ApsPlanningPersistenceConfiguration.class, ApsExecutionPersistenceConfiguration.class,
                        ApsReportingPersistenceConfiguration.class)
                .withPropertyValues(
                        "aps.enabled=true",
                        "aps.persistence.enabled=true",
                        "aps.datasource.enabled=true",
                        "aps.flyway.enabled=true",
                        "aps.reporting.enabled=true",
                        "aps.site.code=SITE-01",
                        "aps.site.zone-id=Asia/Shanghai",
                        "aps.datasource.url=" + withUtc(schemaUrl),
                        "aps.datasource.username=" + username,
                        "aps.datasource.password=" + password)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("apsDataSource");
                    assertThat(context).hasBean("apsSqlSessionFactory");
                    assertThat(context).hasBean("apsTransactionManager");
                    assertThat(context).hasBean("apsFlyway");
                    DataSource dataSource = context.getBean("apsDataSource", DataSource.class);
                    assertThat(dataSource).isInstanceOf(DruidDataSource.class);
                    Flyway configuredFlyway = context.getBean("apsFlyway", Flyway.class);
                    assertThat(configuredFlyway.info().current().getVersion().getVersion()).isEqualTo("006");
                    verifyResourceSlice(context.getBean(ResourceManagementService.class));
                    verifyManufacturingSlice(context.getBean(ResourceManagementService.class),
                            context.getBean(RoutingManagementService.class), context.getBean(OrderManagementService.class),
                            context.getBean(SolverInputCompiler.class), context.getBean(PlanRequestService.class),
                            context.getBean(PlanRepository.class), context.getBean(PlanWorkbenchService.class),
                            context.getBean(PlanLockService.class), context.getBean(PlanAdjustmentService.class),
                            context.getBean(PlanStructuralAdjustmentService.class),
                            context.getBean(PlanPublishService.class), context.getBean(PlanCandidateLifecycleService.class),
                            context.getBean(ExecutionLifecycleService.class),
                            context.getBean(ProductionReportingService.class),
                            context.getBean(ReportingService.class),
                            context.getBean(ExecutionRepository.class), context.getBean(QuantityRepository.class),
                            context.getBean(OrderRepository.class),
                            context.getBean(ApsTransactionOperations.class), dataSource);
                });
    }

    private void verifyResourceSlice(ResourceManagementService service)
    {
        ApsAuditActor actor = new ApsAuditActor("200", "planner");
        ResourceAccessScope admin = new ResourceAccessScope("1", true);
        ResourceAccessScope manager = new ResourceAccessScope("200", false);
        ResourceAccessScope outsider = new ResourceAccessScope("300", false);

        Workshop workshop = service.saveWorkshop(admin, actor,
                new Workshop(null, "WS-RESOURCE", "资源车间", "200", "ACTIVE", null, 0));
        WorkCenter center = service.saveWorkCenter(manager, actor,
                new WorkCenter(null, workshop.id(), "WELD-CENTER", "焊接中心", "LABOR", BigDecimal.ONE,
                        "COUNT", "ACTIVE", null, 0));
        assertThat(service.listWorkshops(manager)).extracting(Workshop::id).containsExactly(workshop.id());
        assertThat(service.listWorkshops(outsider)).isEmpty();
        assertThatThrownBy(() -> service.listWorkCenters(outsider, workshop.id()))
                .isInstanceOf(ApsBusinessException.class);

        Resource levelTwo = person(service, manager, actor, workshop.id(), center.id(), "PERSON-A", "甲");
        Resource onLeave = person(service, manager, actor, workshop.id(), center.id(), "PERSON-B", "乙");
        Resource qualified = person(service, manager, actor, workshop.id(), center.id(), "PERSON-C", "丙");
        Instant shiftStart = Instant.parse("2026-09-14T00:00:00Z");
        Instant taskStart = Instant.parse("2026-09-14T01:00:00Z");
        Instant taskEnd = Instant.parse("2026-09-14T02:00:00Z");
        Instant shiftEnd = Instant.parse("2026-09-14T08:00:00Z");

        addSkill(service, manager, actor, levelTwo.id(), "WELD", 2);
        addSkill(service, manager, actor, onLeave.id(), "WELD", 3);
        addSkill(service, manager, actor, qualified.id(), "WELD", 3);
        addSkill(service, manager, actor, qualified.id(), "INSPECT", 4);
        addWindow(service, manager, actor, levelTwo.id(), AvailabilityType.AVAILABLE, shiftStart, shiftEnd);
        addWindow(service, manager, actor, onLeave.id(), AvailabilityType.AVAILABLE, shiftStart, shiftEnd);
        addWindow(service, manager, actor, onLeave.id(), AvailabilityType.LEAVE, taskStart, taskEnd);
        addWindow(service, manager, actor, qualified.id(), AvailabilityType.AVAILABLE, shiftStart, shiftEnd);

        assertThat(service.candidates(manager, workshop.id(), "WELD", 3, taskStart, taskEnd))
                .extracting(candidate -> candidate.resourceCode()).containsExactly("PERSON-C");
        assertThat(service.listResources(manager, workshop.id(), null)).hasSize(3);
        assertThat(service.listSkills(manager, qualified.id())).hasSize(2);
        assertThat(service.netAvailability(manager, onLeave.id(), shiftStart, shiftEnd)).hasSize(2);
        assertThat(service.readiness(manager, workshop.id(), taskStart))
                .anyMatch(issue -> issue.code().equals("AVAILABILITY_CONFLICT") && issue.objectId().equals(onLeave.id()));

        int beforeImport = service.listAvailability(manager, qualified.id()).size();
        Availability duplicateOne = new Availability(null, qualified.id(), AvailabilityType.OVERTIME,
                shiftEnd, shiftEnd.plusSeconds(3600), BigDecimal.ONE, "IMPORT", "BATCH-1", null, 0);
        Availability duplicateTwo = new Availability(null, qualified.id(), AvailabilityType.OVERTIME,
                shiftEnd, shiftEnd.plusSeconds(3600), BigDecimal.ONE, "IMPORT", "BATCH-1", null, 0);
        assertThatThrownBy(() -> service.importAvailability(manager, actor, List.of(duplicateOne, duplicateTwo)))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.listAvailability(manager, qualified.id())).hasSize(beforeImport);

        Workshop updated = service.saveWorkshop(manager, actor,
                new Workshop(workshop.id(), workshop.code(), "资源车间-更新", workshop.managerUserId(),
                        workshop.status(), workshop.remark(), workshop.rowVersion()));
        assertThat(updated.rowVersion()).isEqualTo(1);
        Workshop stale = new Workshop(workshop.id(), workshop.code(), "过期更新", workshop.managerUserId(),
                workshop.status(), workshop.remark(), workshop.rowVersion());
        assertThatThrownBy(() -> service.saveWorkshop(manager, actor, stale))
                .isInstanceOf(ApsBusinessException.class);
    }

    private void verifyManufacturingSlice(ResourceManagementService resources, RoutingManagementService routings,
            OrderManagementService orders, SolverInputCompiler compiler, PlanRequestService planRequests,
            PlanRepository plans, PlanWorkbenchService workbench, PlanLockService lockService,
            PlanAdjustmentService adjustmentService, PlanStructuralAdjustmentService structuralAdjustmentService,
            PlanPublishService publishService,
            PlanCandidateLifecycleService candidateLifecycle,
            ExecutionLifecycleService executionLifecycle,
            ProductionReportingService reporting,
            ReportingService reports,
            ExecutionRepository executionRepository, QuantityRepository quantityRepository,
            OrderRepository orderRepository,
            ApsTransactionOperations transactions, DataSource dataSource)
    {
        ApsAuditActor actor = new ApsAuditActor("201", "technologist");
        ResourceAccessScope admin = new ResourceAccessScope("1", true);
        Workshop workshop = resources.saveWorkshop(admin, actor,
                new Workshop(null, "WS-MFG", "制造车间", "201", "ACTIVE", null, 0));
        WorkCenter center = resources.saveWorkCenter(admin, actor,
                new WorkCenter(null, workshop.id(), "MFG-CENTER", "加工中心", "MACHINE", BigDecimal.ONE,
                        "COUNT", "ACTIVE", null, 0));
        Resource machine = resources.saveResource(admin, actor, new Resource(null, workshop.id(), center.id(),
                "MACHINE-MFG", "制造测试设备", ResourceType.MACHINE, null, null, BigDecimal.ONE,
                "COUNT", "ACTIVE", null, 0));
        addWindow(resources, admin, actor, machine.id(), AvailabilityType.AVAILABLE,
                Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"));

        Item productA = routings.saveItem(new Item(null, "PROD-A", "产品 A", ItemType.PRODUCT,
                null, "PCS", "ACTIVE", null, 0), actor.username());
        Item productB = routings.saveItem(new Item(null, "PROD-B", "产品 B", ItemType.PRODUCT,
                null, "PCS", "ACTIVE", null, 0), actor.username());
        OperationSpec operation = createActiveOperation(routings, center.id(), actor.username());

        RouteVersion routeA = createAndPublishRoute(routings, productA.id(), "ROUTE-A", "V1", operation.id(),
                List.of("A-CUT", "A-WELD"),
                List.of(new RouteEdgeDraft(null, "A-CUT", "A-WELD", DependencyType.FINISH,
                        null, null, null, 0, false)), actor.username());
        RouteVersion routeB = createAndPublishRoute(routings, productB.id(), "ROUTE-B", "V1", operation.id(),
                List.of("B-ASSEMBLE"), List.of(), actor.username());

        OrderLine lineA = orderLine(1, productA.id(), routeA.id(), new BigDecimal("10"), List.of(), List.of());
        ComponentSpec transfer = new ComponentSpec(1, "B-ASSEMBLE", productA.id(), 1, "A-WELD",
                DemandType.TRANSFER, new BigDecimal("0.5"), "PCS", new BigDecimal("2"));
        LineDependencySpec explicitCrossProduct = new LineDependencySpec(1, "A-WELD", "B-ASSEMBLE",
                DependencyType.QUANTITY, new BigDecimal("5"), null, new BigDecimal("2"), 30, false);
        OrderLine lineB = orderLine(2, productB.id(), routeB.id(), new BigDecimal("5"),
                List.of(transfer), List.of(explicitCrossProduct));
        ProductionOrder request = order("ERP-ORDER-001", "ERP", "EXT-001", List.of(lineA, lineB));

        ProductionOrder firstImport = orders.createOrImport(request, actor.username());
        ProductionOrder secondImport = orders.createOrImport(request, actor.username());
        assertThat(secondImport.id()).isEqualTo(firstImport.id());
        assertThat(orders.listOrders().stream().filter(value -> "EXT-001".equals(value.externalId()))).hasSize(1);
        ProductionOrder changedPayload = new ProductionOrder(null, request.orderNo(), request.sourceSystem(),
                request.externalId(), null, null, 80, null, null, "DRAFT", null, request.lines(), 0);
        assertThatThrownBy(() -> orders.createOrImport(changedPayload, actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("幂等键");

        var firstExpansion = orders.expandOrder(firstImport.id(), actor.username());
        var repeatedExpansion = orders.expandOrder(firstImport.id(), actor.username());
        assertThat(firstExpansion.reused()).isFalse();
        assertThat(repeatedExpansion.reused()).isTrue();
        assertThat(firstExpansion.lots()).hasSize(2);
        assertThat(firstExpansion.tasks()).hasSize(3);
        assertThat(firstExpansion.dependencies()).hasSize(2);
        assertThat(firstExpansion.materialDemands()).singleElement()
                .satisfies(value -> assertThat(value.requiredQty()).isEqualByComparingTo("2.5"));
        assertThat(firstExpansion.tasks()).allSatisfy(task -> {
            assertThat(task.routeVersionId()).isIn(routeA.id(), routeB.id());
            assertThat(task.runSeconds()).isPositive();
            assertThat(task.requirementSnapshot()).isNotEmpty();
        });
        String parentLotId = firstExpansion.lots().stream()
                .filter(lot -> lot.orderLineId().equals(firstImport.lines().get(0).id()))
                .findFirst().orElseThrow().id();
        var splitLot = orders.createDerivedLot(parentLotId, LotType.SPLIT, new BigDecimal("2"), "并行加工", actor.username());
        var splitExpansion = orders.getExpansion(firstImport.id());
        var reducedParent = splitExpansion.lots().stream().filter(lot -> lot.id().equals(parentLotId)).findFirst().orElseThrow();
        assertThat(reducedParent.plannedQty()).isEqualByComparingTo("8");
        assertThat(splitExpansion.tasks().stream().filter(task -> task.productionLotId().equals(parentLotId))).allSatisfy(task ->
                assertThat(task.taskQty()).isEqualByComparingTo("8"));
        assertThat(reducedParent.plannedQty().add(splitLot.plannedQty()))
                .as("拆批必须转移数量，不能把子批叠加成额外产量").isEqualByComparingTo("10");
        var reworkLot = orders.createDerivedLot(parentLotId, LotType.REWORK, BigDecimal.ONE, "质量返工", actor.username());
        var replenishmentLot = orders.createDerivedLot(parentLotId, LotType.REPLENISH, BigDecimal.ONE, "短缺补产", actor.username());
        assertThat(List.of(splitLot, reworkLot, replenishmentLot)).extracting(lot -> lot.lotType())
                .containsExactly(LotType.SPLIT, LotType.REWORK, LotType.REPLENISH);
        assertThat(orders.getExpansion(firstImport.id()).lots()).hasSize(5)
                .allSatisfy(lot -> assertThat(lot.parentLotId() == null || lot.parentLotId().equals(parentLotId)).isTrue());
        assertThat(orders.getExpansion(firstImport.id()).tasks()).hasSize(9);
        ProductionOrder released = orders.releaseOrder(firstImport.id(), firstImport.rowVersion(), actor.username());
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.lines()).allMatch(line -> "RELEASED".equals(line.status()));
        assertThat(orders.getExpansion(firstImport.id()).lots()).allMatch(lot -> "RELEASED".equals(lot.status()));
        CompileRequest compileRequest = new CompileRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "SITE_01", List.of(workshop.id()), List.of(firstImport.id()), Instant.parse("2026-09-14T00:00:00Z"),
                7, 3, Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-14T00:00:00Z"), 60,
                "Asia/Shanghai", "aps-cpsat-v1", 30, 1, 1, 0, 0);
        var compiled = compiler.compile(admin, compileRequest);
        assertThat(compiled.readiness().validationStatus().name()).isEqualTo("PASS");
        assertThat(compiled.input().tasks()).hasSize(9);
        assertThat(compiled.input().resources()).extracting(value -> value.resourceId()).containsExactly(machine.id());
        assertThat(compiled.input().inputHash()).hasSize(64).doesNotContain(" ");
        var createdPlan = planRequests.create(admin, compileRequest, actor.username());
        assertThat(createdPlan.reused()).isFalse();
        assertThat(planRequests.create(admin, compileRequest, actor.username()).reused()).isTrue();
        CompileRequest changedPlanPayload = new CompileRequest(compileRequest.requestId(), compileRequest.planVersionId(),
                compileRequest.siteCode(), compileRequest.workshopIds(), compileRequest.orderIds(),
                compileRequest.capturedAt(), compileRequest.definitionRevision() + 1, compileRequest.executionRevision(),
                compileRequest.horizonStartAt(), compileRequest.detailEndAt(), compileRequest.horizonEndAt(),
                compileRequest.planningAnchorAt(), compileRequest.timeUnitSeconds(), compileRequest.displayTimeZone(),
                compileRequest.modelVersion(), compileRequest.maxSolveSeconds(), compileRequest.randomSeed(),
                compileRequest.solverSearchThreads(), compileRequest.absoluteGapLimit(), compileRequest.relativeGapLimit());
        assertThatThrownBy(() -> planRequests.create(admin, changedPlanPayload, actor.username()))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode()).isEqualTo(
                        com.ruoyi.aps.application.foundation.ApsErrorCode.IDEMPOTENCY_CONFLICT));
        PlanRepository.ClaimedPlan claimed = transactions.required(() -> plans.claimNext(actor.username())).orElseThrow();
        assertThat(claimed.plan().status()).isEqualTo("SOLVING");
        assertThat(claimed.input().inputHash()).isEqualTo(compiled.input().inputHash());
        assertThat(planRequests.cancel(admin, compileRequest.requestId(), actor.username()).plan().status())
                .isEqualTo("CANCELLED");

        ProductionOrder solverOrder = orders.createOrImport(order("ERP-ORDER-SOLVER", "ERP", "EXT-SOLVER",
                List.of(orderLine(1, productB.id(), routeB.id(), new BigDecimal("2"), List.of(), List.of()))),
                actor.username());
        orders.expandOrder(solverOrder.id(), actor.username());
        solverOrder = orders.releaseOrder(solverOrder.id(), solverOrder.rowVersion(), actor.username());
        CompileRequest solverRequest = compileRequest(workshop.id(), solverOrder.id(), "solver-success");
        planRequests.create(admin, solverRequest, actor.username());
        PlanRepository.ClaimedPlan solverClaim = transactions.required(() -> plans.claimNext(actor.username())).orElseThrow();
        SolverResult solverResult = new CpSatFiniteCapacitySolver().solve(solverClaim.input(), solverRequest.capturedAt());
        assertThat(solverResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(transactions.required(() -> plans.storeResult(solverClaim, solverResult, actor.username()))).isEqualTo(1);
        var solved = planRequests.status(admin, solverRequest.requestId());
        assertThat(solved.plan().status()).isEqualTo("FEASIBLE");
        assertThat(solved.solverStatus()).isIn(SolverResult.SolverStatus.OPTIMAL, SolverResult.SolverStatus.FEASIBLE);
        assertPlanCandidateRows(dataSource, solverClaim.plan().id(), 1, 1, 1, 1);

        PlanRepository.PlanDetail sourceCandidate = workbench.detail(admin, solverClaim.plan().id());
        PlanRepository.PlanJob sourceJob = sourceCandidate.jobs().get(0);
        String adjustmentRequestId = UUID.nameUUIDFromBytes("mysql-adjustment".getBytes()).toString();
        PlanAdjustmentService.Adjustment adjustment = new PlanAdjustmentService.Adjustment(adjustmentRequestId,
                sourceCandidate.plan().id(), sourceCandidate.plan().rowVersion(), solverRequest.capturedAt(), "JOB",
                sourceJob.id(), sourceJob.startAt().plusSeconds(60), sourceJob.endAt().plusSeconds(60), null,
                "真实 MySQL 向后拖动一分钟");
        PlanAdjustmentService.CreatedAdjustment adjusted = adjustmentService.create(admin, adjustment,
                actor.username());
        assertThat(adjusted.reused()).isFalse();
        assertThat(adjusted.plan().baseVersionId()).isEqualTo(sourceCandidate.plan().id());
        assertThat(adjusted.baseCandidateHash()).isEqualTo(sourceCandidate.candidateHash());
        assertThat(adjusted.impact().affectedTaskIds()).containsExactly(
                sourceJob.members().get(0).taskId());
        assertThat(adjusted.impact().affectedResourceIds()).containsExactly(machine.id());
        assertThat(adjustmentService.create(admin, adjustment, actor.username()).reused()).isTrue();
        PlanRepository.ClaimedPlan adjustedClaim = transactions.required(() -> plans.claimNext(actor.username()))
                .orElseThrow();
        assertThat(adjustedClaim.plan().id()).isEqualTo(adjusted.plan().id());
        assertThat(adjustedClaim.input().baseVersion().planVersionId()).isEqualTo(sourceCandidate.plan().id());
        assertThat(adjustedClaim.input().locks()).singleElement().satisfies(lock -> {
            assertThat(lock.targetType()).isEqualTo("JOB");
            assertThat(lock.lockType()).isEqualTo("TIME");
            assertThat(lock.lockedStartAt()).isEqualTo(sourceJob.startAt().plusSeconds(60));
        });
        SolverResult adjustedResult = new CpSatFiniteCapacitySolver().solve(adjustedClaim.input(),
                solverRequest.capturedAt());
        assertThat(adjustedResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(transactions.required(() -> plans.storeResult(adjustedClaim, adjustedResult,
                actor.username()))).isEqualTo(1);
        PlanWorkbenchService.PlanComparison adjustedComparison = workbench.compare(admin,
                adjustedClaim.plan().id(), sourceCandidate.plan().id());
        assertThat(adjustedComparison.summary().changed()).isEqualTo(1);
        assertThat(adjustedComparison.jobs()).singleElement().satisfies(change ->
                assertThat(change.dimensions()).containsExactly("TIME"));

        PlanRepository.PlanDetail baselineLocked = lockService.create(admin, new PlanLockService.CreateLock(
                sourceCandidate.plan().id(), sourceCandidate.plan().rowVersion(), "SEGMENT",
                sourceCandidate.segments().get(0).id(), "TIME", null, "测试继承分段时间锁"), actor.username());
        baselineLocked = lockService.create(admin, new PlanLockService.CreateLock(baselineLocked.plan().id(),
                baselineLocked.plan().rowVersion(), "ALLOCATION", baselineLocked.allocations().get(0).id(),
                "RESOURCE", baselineLocked.allocations().get(0).resourceId(), "测试继承分配资源锁"),
                actor.username());
        PlanPublishService.PublishedPlan firstPublished = publishService.publish(admin, baselineLocked.plan().id(),
                baselineLocked.plan().rowVersion(), "首个系统内正式计划", actor.username());
        assertThat(firstPublished.detail().plan().status()).isEqualTo("PUBLISHED");
        assertThat(firstPublished.supersededVersionId()).isNull();
        assertThat(firstPublished.outboundStatus()).isEqualTo("NOT_APPLICABLE");
        assertThat(publishService.publish(admin, baselineLocked.plan().id(), baselineLocked.plan().rowVersion(),
                "幂等重放", actor.username()).reused()).isTrue();
        PlanRepository.BaselineSnapshot baseline = plans.findCurrentPublishedBaseline(solverClaim.plan().id())
                .orElseThrow();
        assertThat(baseline.jobs()).singleElement().satisfies(value -> {
            assertThat(value.memberTaskIds()).containsExactly(solverClaim.input().tasks().get(0).taskId());
            assertThat(value.resourceIds()).containsExactly(machine.id());
        });
        assertThat(baseline.locks()).extracting(PlanRepository.BaselineLock::targetType)
                .containsExactlyInAnyOrder("SEGMENT", "ALLOCATION");
        PlanRepository.PlanDetail baselineDetail = workbench.detail(admin, solverClaim.plan().id());
        assertThat(baselineDetail.candidateHash()).isEqualTo(solverResult.candidateHash());
        assertThat(baselineDetail.jobs()).singleElement().satisfies(value ->
                assertThat(value.members()).singleElement().satisfies(member ->
                        assertThat(member.taskId()).isEqualTo(solverClaim.input().tasks().get(0).taskId())));
        assertThat(baselineDetail.segments()).hasSize(1);
        assertThat(baselineDetail.allocations()).hasSize(1);
        assertThat(baselineDetail.locks()).extracting(PlanRepository.PlanLock::targetType)
                .containsExactlyInAnyOrder("SEGMENT", "ALLOCATION");
        CompileRequest replanSource = compileRequest(workshop.id(), solverOrder.id(), "solver-replan");
        CompileRequest replanRequest = new CompileRequest(replanSource.requestId(), replanSource.planVersionId(),
                replanSource.siteCode(), replanSource.workshopIds(), replanSource.orderIds(), replanSource.capturedAt(),
                replanSource.definitionRevision(), replanSource.executionRevision(), replanSource.horizonStartAt(),
                replanSource.detailEndAt(), replanSource.horizonEndAt(), replanSource.planningAnchorAt(),
                replanSource.timeUnitSeconds(), replanSource.displayTimeZone(), replanSource.modelVersion(),
                replanSource.maxSolveSeconds(), replanSource.randomSeed(), replanSource.solverSearchThreads(),
                replanSource.absoluteGapLimit(), replanSource.relativeGapLimit(), List.of(),
                new BaseVersionRequest(baseline.plan().id(), baseline.plan().inputHash(),
                        baseline.jobs().get(0).startAt()));
        CompileRequest staleBaseRequest = new CompileRequest(replanRequest.requestId(), replanRequest.planVersionId(),
                replanRequest.siteCode(), replanRequest.workshopIds(), replanRequest.orderIds(), replanRequest.capturedAt(),
                replanRequest.definitionRevision(), replanRequest.executionRevision(), replanRequest.horizonStartAt(),
                replanRequest.detailEndAt(), replanRequest.horizonEndAt(), replanRequest.planningAnchorAt(),
                replanRequest.timeUnitSeconds(), replanRequest.displayTimeZone(), replanRequest.modelVersion(),
                replanRequest.maxSolveSeconds(), replanRequest.randomSeed(), replanRequest.solverSearchThreads(),
                replanRequest.absoluteGapLimit(), replanRequest.relativeGapLimit(), replanRequest.sharedBatches(),
                new BaseVersionRequest(baseline.plan().id(), "f".repeat(64), baseline.jobs().get(0).endAt()));
        assertThatThrownBy(() -> compiler.compile(admin, staleBaseRequest))
                .isInstanceOf(ApsValidationException.class).hasMessageContaining("重排基线");
        var createdReplan = planRequests.create(admin, replanRequest, actor.username());
        assertThat(createdReplan.plan().baseVersionId()).isEqualTo(baseline.plan().id());
        PlanRepository.ClaimedPlan replanClaim = transactions.required(() -> plans.claimNext(actor.username())).orElseThrow();
        assertThat(replanClaim.input().locks()).extracting(SolverInput.PlanLock::targetType)
                .containsExactlyInAnyOrder("SEGMENT", "ALLOCATION");
        SolverResult replanResult = new CpSatFiniteCapacitySolver().solve(replanClaim.input(), replanRequest.capturedAt());
        assertThat(replanResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(replanResult.candidate().jobs().get(0).startAt()).isEqualTo(baseline.jobs().get(0).startAt());
        assertThat(replanResult.candidate().jobs().get(0).endAt()).isEqualTo(baseline.jobs().get(0).endAt());
        assertThat(replanResult.candidate().segments().get(0).startAt())
                .isEqualTo(solverResult.candidate().segments().get(0).startAt());
        assertThat(replanResult.candidate().allocations().get(0).resourceId())
                .isEqualTo(solverResult.candidate().allocations().get(0).resourceId());
        assertThat(transactions.required(() -> plans.storeResult(replanClaim, replanResult, actor.username()))).isEqualTo(1);
        PlanWorkbenchService.PlanComparison comparison = workbench.compare(admin, replanClaim.plan().id(), null);
        assertThat(comparison.baseVersionId()).isEqualTo(solverClaim.plan().id());
        assertThat(comparison.summary().added()).isZero();
        assertThat(comparison.summary().removed()).isZero();
        assertThat(comparison.summary().changed()).isZero();
        assertThat(comparison.summary().unchanged()).isEqualTo(1);

        PlanRepository.PlanDetail replanDetail = workbench.detail(admin, replanClaim.plan().id());
        assertThat(replanDetail.locks()).extracting(PlanRepository.PlanLock::targetType)
                .containsExactlyInAnyOrder("SEGMENT", "ALLOCATION");
        PlanRepository.PlanAllocation replanAllocation = replanDetail.allocations().get(0);
        PlanRepository.PlanDetail lockedCandidate = lockService.create(admin, new PlanLockService.CreateLock(
                replanDetail.plan().id(), replanDetail.plan().rowVersion(), "ALLOCATION", replanAllocation.id(),
                "FULL", null, "真实 MySQL 候选全锁"), actor.username());
        assertThat(lockedCandidate.plan().rowVersion()).isEqualTo(replanDetail.plan().rowVersion() + 1);
        assertThat(lockedCandidate.locks()).hasSize(3);
        PlanRepository.PlanLock createdLock = lockedCandidate.locks().stream()
                .filter(lock -> "真实 MySQL 候选全锁".equals(lock.reason())).findFirst().orElseThrow();
        assertThat(createdLock).satisfies(lock -> {
            assertThat(lock.targetType()).isEqualTo("ALLOCATION");
            assertThat(lock.lockType()).isEqualTo("FULL");
            assertThat(lock.lockedResourceId()).isEqualTo(replanAllocation.resourceId());
            assertThat(lock.lockedStartAt()).isEqualTo(lockedCandidate.segments().get(0).startAt());
            assertThat(lock.lockedEndAt()).isEqualTo(lockedCandidate.segments().get(0).endAt());
        });
        PlanRepository.PlanDetail unlockedCandidate = lockService.delete(admin, replanDetail.plan().id(),
                createdLock.id(), lockedCandidate.plan().rowVersion(), createdLock.rowVersion(), actor.username());
        assertThat(unlockedCandidate.plan().rowVersion()).isEqualTo(lockedCandidate.plan().rowVersion() + 1);
        assertThat(unlockedCandidate.locks()).extracting(PlanRepository.PlanLock::targetType)
                .containsExactlyInAnyOrder("SEGMENT", "ALLOCATION");
        createPublishFailureTrigger(dataSource);
        try
        {
            assertThatThrownBy(() -> publishService.publish(admin, unlockedCandidate.plan().id(),
                    unlockedCandidate.plan().rowVersion(), "发布故障注入", actor.username()))
                    .isInstanceOf(RuntimeException.class);
        }
        finally
        {
            dropPublishFailureTrigger(dataSource);
        }
        assertThat(workbench.detail(admin, solverClaim.plan().id()).plan().status()).isEqualTo("PUBLISHED");
        assertThat(workbench.detail(admin, unlockedCandidate.plan().id()).plan().status()).isEqualTo("FEASIBLE");
        assertThat(plans.findCurrentPublishedBaseline(solverClaim.plan().id()).orElseThrow().plan().id())
                .isEqualTo(solverClaim.plan().id());
        PlanPublishService.PublishedPlan replacement = publishService.publish(admin, unlockedCandidate.plan().id(),
                unlockedCandidate.plan().rowVersion(), "替换当前正式计划", actor.username());
        assertThat(replacement.supersededVersionId()).isEqualTo(solverClaim.plan().id());
        assertThat(replacement.detail().plan().status()).isEqualTo("PUBLISHED");
        assertThat(plans.findCurrentPublishedBaseline(replanClaim.plan().id())).isPresent();
        assertThat(workbench.detail(admin, solverClaim.plan().id()).plan().status()).isEqualTo("SUPERSEDED");
        PlanRepository.PlanDetail staleAdjustment = workbench.detail(admin, adjustedClaim.plan().id());
        assertThatThrownBy(() -> publishService.publish(admin, staleAdjustment.plan().id(),
                staleAdjustment.plan().rowVersion(), "旧基线并发发布", actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("当前正式版本已变化");
        PlanCandidateLifecycleService.DiscardedCandidate discarded = candidateLifecycle.discard(admin,
                staleAdjustment.plan().id(), staleAdjustment.plan().rowVersion(), "已有更新候选，旧方案废弃",
                actor.username());
        assertThat(discarded.detail().plan().status()).isEqualTo("CANCELLED");
        assertThat(discarded.detail().jobs()).isNotEmpty();
        assertThat(plans.findCurrentPublishedBaseline(replanClaim.plan().id())).isPresent();
        assertThatThrownBy(() -> publishService.publish(admin, discarded.detail().plan().id(),
                discarded.detail().plan().rowVersion(), "废弃候选禁止发布", actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("只有可行候选");

        ProductionOrder insertedOrder = orders.createOrImport(order("ERP-ORDER-INSERT", "ERP", "EXT-INSERT",
                List.of(orderLine(1, productB.id(), routeB.id(), BigDecimal.ONE, List.of(), List.of()))),
                actor.username());
        orders.expandOrder(insertedOrder.id(), actor.username());
        insertedOrder = orders.releaseOrder(insertedOrder.id(), insertedOrder.rowVersion(), actor.username());
        String insertRequestId = UUID.nameUUIDFromBytes("mysql-structural-insert".getBytes()).toString();
        var insertCommand = new PlanStructuralAdjustmentService.StructuralAdjustment(insertRequestId,
                replacement.detail().plan().id(), replacement.detail().plan().rowVersion(),
                Instant.parse("2026-09-14T00:00:00Z"), PlanStructuralAdjustmentService.Action.INSERT_ORDER,
                insertedOrder.id(), null, null, null, List.of(), "紧急订单插单");
        var inserted = structuralAdjustmentService.create(admin, insertCommand, actor.username());
        assertThat(inserted.reused()).isFalse();
        assertThat(inserted.plan().baseVersionId()).isEqualTo(replacement.detail().plan().id());
        assertThat(inserted.impact().affectedTaskIds()).containsAll(
                orders.getExpansion(insertedOrder.id()).tasks().stream().map(value -> value.id()).toList());
        assertThat(inserted.impact().affectedResourceIds()).contains(machine.id());
        assertThat(structuralAdjustmentService.create(admin, insertCommand, actor.username()).reused()).isTrue();
        var changedInsertCommand = new PlanStructuralAdjustmentService.StructuralAdjustment(insertRequestId,
                insertCommand.basePlanVersionId(), insertCommand.expectedBaseRowVersion(), insertCommand.capturedAt(),
                insertCommand.action(), insertCommand.orderId(), null, null, null, List.of(), "不同插单原因");
        assertThatThrownBy(() -> structuralAdjustmentService.create(admin, changedInsertCommand, actor.username()))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode()).isEqualTo(
                        com.ruoyi.aps.application.foundation.ApsErrorCode.IDEMPOTENCY_CONFLICT));
        PlanRepository.ClaimedPlan insertClaim = transactions.required(() -> plans.claimNext(actor.username()))
                .orElseThrow();
        assertThat(insertClaim.plan().id()).isEqualTo(inserted.plan().id());
        assertThat(insertClaim.input().tasks()).hasSize(2);
        SolverResult insertResult = new CpSatFiniteCapacitySolver().solve(insertClaim.input(),
                insertClaim.input().capturedAt());
        assertThat(insertResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(transactions.required(() -> plans.storeResult(insertClaim, insertResult, actor.username())))
                .isEqualTo(1);

        OperationSpec batchOperation = createActiveBatchOperation(routings, center.id(), actor.username());
        Item batchProductA = routings.saveItem(new Item(null, "BATCH-A", "合批产品 A", ItemType.PRODUCT,
                null, "PCS", "ACTIVE", null, 0), actor.username());
        Item batchProductB = routings.saveItem(new Item(null, "BATCH-B", "合批产品 B", ItemType.PRODUCT,
                null, "PCS", "ACTIVE", null, 0), actor.username());
        RouteVersion batchRouteA = createAndPublishRoute(routings, batchProductA.id(), "ROUTE-BATCH-A", "V1",
                batchOperation.id(), List.of("BATCH-RUN-A"), List.of(), actor.username());
        RouteVersion batchRouteB = createAndPublishRoute(routings, batchProductB.id(), "ROUTE-BATCH-B", "V1",
                batchOperation.id(), List.of("BATCH-RUN-B"), List.of(), actor.username());
        ProductionOrder batchOrderA = orders.createOrImport(order("ERP-BATCH-A", "ERP", "EXT-BATCH-A",
                List.of(orderLine(1, batchProductA.id(), batchRouteA.id(), new BigDecimal("4"), List.of(), List.of()))),
                actor.username());
        ProductionOrder batchOrderB = orders.createOrImport(order("ERP-BATCH-B", "ERP", "EXT-BATCH-B",
                List.of(orderLine(1, batchProductB.id(), batchRouteB.id(), new BigDecimal("6"), List.of(), List.of()))),
                actor.username());
        orders.expandOrder(batchOrderA.id(), actor.username());
        orders.expandOrder(batchOrderB.id(), actor.username());
        batchOrderA = orders.releaseOrder(batchOrderA.id(), batchOrderA.rowVersion(), actor.username());
        batchOrderB = orders.releaseOrder(batchOrderB.id(), batchOrderB.rowVersion(), actor.username());
        CompileRequest batchBaseSource = compileRequest(workshop.id(),
                List.of(solverOrder.id(), batchOrderA.id(), batchOrderB.id()), "batch-base");
        CompileRequest batchBaseRequest = new CompileRequest(batchBaseSource.requestId(),
                batchBaseSource.planVersionId(), batchBaseSource.siteCode(), batchBaseSource.workshopIds(),
                batchBaseSource.orderIds(), batchBaseSource.capturedAt(), batchBaseSource.definitionRevision(),
                batchBaseSource.executionRevision(), batchBaseSource.horizonStartAt(),
                batchBaseSource.detailEndAt(), batchBaseSource.horizonEndAt(),
                batchBaseSource.planningAnchorAt(), batchBaseSource.timeUnitSeconds(),
                batchBaseSource.displayTimeZone(), batchBaseSource.modelVersion(),
                batchBaseSource.maxSolveSeconds(), batchBaseSource.randomSeed(),
                batchBaseSource.solverSearchThreads(), batchBaseSource.absoluteGapLimit(),
                batchBaseSource.relativeGapLimit(), List.of(), new BaseVersionRequest(
                        replacement.detail().plan().id(), replacement.detail().plan().inputHash(),
                        replacement.detail().jobs().get(0).startAt()));
        planRequests.create(admin, batchBaseRequest, actor.username());
        PlanRepository.ClaimedPlan batchBaseClaim = transactions.required(() -> plans.claimNext(actor.username()))
                .orElseThrow();
        SolverResult batchBaseResult = new CpSatFiniteCapacitySolver().solve(batchBaseClaim.input(),
                batchBaseClaim.input().capturedAt());
        assertThat(batchBaseResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(transactions.required(() -> plans.storeResult(batchBaseClaim, batchBaseResult, actor.username())))
                .isEqualTo(1);
        PlanRepository.PlanDetail batchBaseDetail = workbench.detail(admin, batchBaseClaim.plan().id());
        PlanPublishService.PublishedPlan batchPublished = publishService.publish(admin, batchBaseDetail.plan().id(),
                batchBaseDetail.plan().rowVersion(), "发布合批验证基线", actor.username());
        List<SolverInput.Task> batchTasks = batchBaseClaim.input().tasks().stream()
                .filter(value -> value.operationSpecId().equals(batchOperation.id()))
                .sorted(java.util.Comparator.comparing(SolverInput.Task::taskId)).toList();
        String mergeRequestId = UUID.nameUUIDFromBytes("mysql-structural-merge".getBytes()).toString();
        var mergeMembers = batchTasks.stream().map(value -> new PlanStructuralAdjustmentService.BatchMember(
                value.taskId(), new BigDecimal(value.quantity()))).toList();
        var mergeCommand = new PlanStructuralAdjustmentService.StructuralAdjustment(mergeRequestId,
                batchPublished.detail().plan().id(), batchPublished.detail().plan().rowVersion(),
                Instant.parse("2026-09-14T00:00:00Z"), PlanStructuralAdjustmentService.Action.MERGE_BATCH,
                null, null, null, "TEMP-180-COMPATIBLE", mergeMembers, "两产品进入同一固定炉次");
        var merged = structuralAdjustmentService.create(admin, mergeCommand, actor.username());
        assertThat(merged.reused()).isFalse();
        assertThat(merged.impact().affectedTaskIds()).containsAll(
                batchTasks.stream().map(SolverInput.Task::taskId).sorted().toList());
        assertThat(structuralAdjustmentService.create(admin, mergeCommand, actor.username()).reused()).isTrue();
        PlanRepository.ClaimedPlan mergeClaim = transactions.required(() -> plans.claimNext(actor.username()))
                .orElseThrow();
        assertThat(mergeClaim.plan().id()).isEqualTo(merged.plan().id());
        assertThat(mergeClaim.input().sharedBatchCandidates()).singleElement().satisfies(value -> {
            assertThat(value.compatibilityKey()).isEqualTo("TEMP-180-COMPATIBLE");
            assertThat(value.members()).hasSize(2);
        });
        SolverResult mergeResult = new CpSatFiniteCapacitySolver().solve(mergeClaim.input(),
                mergeClaim.input().capturedAt());
        assertThat(mergeResult.planStatus()).as(() -> mergeResult.problems().toString())
                .isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(mergeResult.candidate().jobs().stream()
                .filter(value -> "SHARED_BATCH".equals(value.jobType())).toList()).singleElement().satisfies(value -> {
            assertThat(value.jobType()).isEqualTo("SHARED_BATCH");
            assertThat(value.memberTaskIds()).hasSize(2);
        });
        assertThat(mergeResult.candidate().allocations()).hasSize(2);
        assertThat(transactions.required(() -> plans.storeResult(mergeClaim, mergeResult, actor.username())))
                .isEqualTo(1);
        PlanRepository.PlanDetail mergeDetail = workbench.detail(admin, mergeClaim.plan().id());
        assertThat(candidateLifecycle.discard(admin, mergeDetail.plan().id(), mergeDetail.plan().rowVersion(),
                "结构调整验证结束后放弃候选", actor.username()).detail().plan().status()).isEqualTo("CANCELLED");
        assertThat(structuralAdjustmentService.create(admin, mergeCommand, actor.username()).reused())
                .as("候选生命周期更新 change_note 后仍应按独立请求指纹幂等重放").isTrue();

        var solverLot = orders.getExpansion(solverOrder.id()).lots().get(0);
        String splitRequestId = UUID.nameUUIDFromBytes("mysql-structural-split".getBytes()).toString();
        var splitCommand = new PlanStructuralAdjustmentService.StructuralAdjustment(splitRequestId,
                batchPublished.detail().plan().id(), batchPublished.detail().plan().rowVersion(),
                Instant.parse("2026-09-14T00:00:00Z"), PlanStructuralAdjustmentService.Action.SPLIT_LOT,
                null, solverLot.id(), new BigDecimal("0.5"), null, List.of(), "分出半件用于并行验证");
        var split = structuralAdjustmentService.create(admin, splitCommand, actor.username());
        assertThat(split.reused()).isFalse();
        assertThat(split.derivedLotId()).isNotNull();
        var splitState = orders.getExpansion(solverOrder.id());
        assertThat(splitState.lots().stream().filter(value -> value.id().equals(solverLot.id()))
                .findFirst().orElseThrow().plannedQty()).isEqualByComparingTo("1.5");
        assertThat(splitState.lots().stream().filter(value -> value.id().equals(split.derivedLotId()))
                .findFirst().orElseThrow().plannedQty()).isEqualByComparingTo("0.5");
        assertThat(structuralAdjustmentService.create(admin, splitCommand, actor.username()).reused()).isTrue();
        assertThat(orders.getExpansion(solverOrder.id()).lots().stream()
                .map(value -> value.plannedQty()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("幂等重放不能再次扣减父批").isEqualByComparingTo("2");
        assertThat(planRequests.cancel(admin, splitRequestId, actor.username()).plan().status())
                .isEqualTo("CANCELLED");

        verifyExecutionLifecycle(resources, admin, actor, workshop.id(), center.id(), machine.id(),
                batchPublished.detail(), compiler, planRequests, plans, workbench, executionLifecycle, reporting,
                executionRepository, quantityRepository, orderRepository, transactions);

        LocalDate baselineDate = LocalDate.ofInstant(firstPublished.detail().plan().updatedAt(),
                ZoneId.of("Asia/Shanghai"));
        assertThat(reports.dailyProduction(admin, baselineDate, null, null).metadata().baselinePlanVersionId())
                .isEqualTo(firstPublished.detail().plan().id());
        assertThat(reports.laborCapacity(admin, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15),
                null, null, null).metadata().siteCode()).isEqualTo("SITE-01");
        assertThat(reports.orderDelivery(admin, firstImport.id()).orders()).singleElement()
                .satisfies(value -> assertThat(value.orderNo()).isEqualTo(firstImport.orderNo()));

        ProductionOrder rollbackOrder = orders.createOrImport(order("ERP-ORDER-ROLLBACK", "ERP", "EXT-ROLLBACK",
                List.of(orderLine(1, productB.id(), routeB.id(), BigDecimal.ONE, List.of(), List.of()))),
                actor.username());
        orders.expandOrder(rollbackOrder.id(), actor.username());
        rollbackOrder = orders.releaseOrder(rollbackOrder.id(), rollbackOrder.rowVersion(), actor.username());
        CompileRequest rollbackRequest = compileRequest(workshop.id(), rollbackOrder.id(), "solver-rollback");
        planRequests.create(admin, rollbackRequest, actor.username());
        PlanRepository.ClaimedPlan rollbackClaim = transactions.required(() -> plans.claimNext(actor.username())).orElseThrow();
        SolverResult valid = new CpSatFiniteCapacitySolver().solve(rollbackClaim.input(), rollbackRequest.capturedAt());
        PlanCandidate brokenCandidate = invalidResourceCandidate(valid.candidate());
        SolverResult broken = new SolverResult(valid.schemaVersion(), valid.contractType(), valid.requestId(),
                valid.planVersionId(), valid.generatedAt(), valid.definitionRevision(), valid.executionRevision(),
                valid.inputHash(), valid.modelVersion(), valid.solverVersion(), valid.planStatus(), valid.solverStatus(),
                valid.resultKind(), valid.solverSummary(), valid.candidateHash(), brokenCandidate, valid.problems());
        assertThatThrownBy(() -> transactions.required(() ->
                plans.storeResult(rollbackClaim, broken, actor.username()))).isInstanceOf(RuntimeException.class);
        assertPlanCandidateRows(dataSource, rollbackClaim.plan().id(), 0, 0, 0, 0);
        assertThat(planRequests.status(admin, rollbackRequest.requestId()).plan().status()).isEqualTo("SOLVING");
        backdatePlan(dataSource, rollbackClaim.plan().id(), Instant.now().minusSeconds(120));
        assertThat(transactions.required(() ->
                plans.recoverStaleSolving(Instant.now().minusSeconds(60), "aps-worker"))).isEqualTo(1);
        var recovered = planRequests.status(admin, rollbackRequest.requestId());
        assertThat(recovered.plan().status()).isEqualTo("FAILED");
        assertThat(recovered.solverStatus()).isEqualTo(SolverResult.SolverStatus.UNKNOWN);
        assertThat(recovered.resultKind()).isNull();
        assertThat(recovered.reasonCodes()).containsExactly("STALE_SOLVING_RECOVERED");

        var routeV2Copy = routings.copyRoute(routeA.id(), "V2", "新增检验节点", actor.username());
        RouteGraphDraft routeV2Graph = new RouteGraphDraft(List.of(
                node(operation.id(), "A-CUT", 10), node(operation.id(), "A-WELD", 20),
                node(operation.id(), "A-INSPECT", 30, true)), List.of(
                        new RouteEdgeDraft(null, "A-CUT", "A-WELD", DependencyType.FINISH,
                                null, null, null, 0, false),
                        new RouteEdgeDraft(null, "A-WELD", "A-INSPECT", DependencyType.FINISH,
                                null, null, null, 0, false)));
        routings.saveGraph(routeV2Copy.route().id(), routeV2Graph, actor.username());
        RouteVersion routeV2 = routings.publishRoute(routeV2Copy.route().id(), 0, actor.username());
        assertThat(orders.getExpansion(firstImport.id()).tasks()).extracting(task -> task.routeVersionId())
                .contains(routeA.id()).doesNotContain(routeV2.id());

        ProductionOrder unexpanded = orders.createOrImport(order("ERP-ORDER-002", "ERP", "EXT-002",
                List.of(orderLine(1, productA.id(), routeA.id(), new BigDecimal("3"), List.of(), List.of()))),
                actor.username());
        OrderLine unexpandedLine = unexpanded.lines().get(0);
        var migration = orders.previewRouteMigration(unexpandedLine.id(), routeV2.id());
        assertThat(migration.addedNodes()).containsExactly("A-INSPECT");
        assertThat(migration.confirmationRequired()).isTrue();
        assertThatThrownBy(() -> orders.confirmRouteMigration(unexpandedLine.id(), routeA.id(), routeV2.id(),
                0, false, actor.username())).isInstanceOf(ApsBusinessException.class);
        assertThat(orders.confirmRouteMigration(unexpandedLine.id(), routeA.id(), routeV2.id(),
                0, true, actor.username()).routeVersionId()).isEqualTo(routeV2.id());

        RouteVersion syncDraft = routings.createRoute(new RouteVersion(null, productB.id(), "ROUTE-B-SYNC", "V1",
                "DRAFT", null, null, "保留来源", null, null, 0), actor.username());
        routings.saveGraph(syncDraft.id(), new RouteGraphDraft(
                List.of(node(operation.id(), "SYNC-A", 10), node(operation.id(), "SYNC-B", 20, true)),
                List.of(new RouteEdgeDraft(null, "SYNC-A", "SYNC-B", DependencyType.SAME_START,
                        null, null, null, 0, false))), actor.username());
        assertThat(routings.validateRoute(syncDraft.id()).issues()).extracting(issue -> issue.code())
                .contains("UNSUPPORTED_SYNC_RULE");
        assertThatThrownBy(() -> routings.publishRoute(syncDraft.id(), 0, actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("发布校验");
        ProductionOrder syncOrder = orders.createOrImport(order("ERP-SYNC-001", "ERP", "EXT-SYNC",
                List.of(orderLine(1, productB.id(), syncDraft.id(), new BigDecimal("2"), List.of(), List.of()))),
                actor.username());
        assertThat(orders.expandOrder(syncOrder.id(), actor.username()).dependencies()).singleElement()
                .satisfies(value -> assertThat(value.dependencyType()).isEqualTo(DependencyType.SAME_START));
        assertThatThrownBy(() -> orders.releaseOrder(syncOrder.id(), 0, actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("结构释放校验");

        RouteVersion oversizedDraft = routings.createRoute(new RouteVersion(null, productB.id(), "ROUTE-B-QTY", "V1",
                "DRAFT", null, null, "超量门槛回滚样例", null, null, 0), actor.username());
        routings.saveGraph(oversizedDraft.id(), new RouteGraphDraft(
                List.of(node(operation.id(), "QTY-A", 10), node(operation.id(), "QTY-B", 20, true)),
                List.of(new RouteEdgeDraft(null, "QTY-A", "QTY-B", DependencyType.QUANTITY,
                        new BigDecimal("100"), null, null, 0, false))), actor.username());
        RouteVersion oversizedRoute = routings.publishRoute(oversizedDraft.id(), 0, actor.username());
        ProductionOrder oversizedOrder = orders.createOrImport(order("ERP-QTY-001", "ERP", "EXT-QTY",
                List.of(orderLine(1, productB.id(), oversizedRoute.id(), new BigDecimal("2"), List.of(), List.of()))),
                actor.username());
        assertThatThrownBy(() -> orders.expandOrder(oversizedOrder.id(), actor.username()))
                .isInstanceOf(ApsBusinessException.class).hasMessageContaining("门槛");
        assertThat(orders.getExpansion(oversizedOrder.id()).lots()).isEmpty();
        assertThat(orders.getExpansion(oversizedOrder.id()).tasks()).isEmpty();
    }

    private void verifyExecutionLifecycle(ResourceManagementService resources, ResourceAccessScope scope,
            ApsAuditActor actor, String workshopId, String centerId, String plannedResourceId,
            PlanRepository.PlanDetail published, SolverInputCompiler compiler, PlanRequestService planRequests,
            PlanRepository plans, PlanWorkbenchService workbench, ExecutionLifecycleService service,
            ProductionReportingService reporting, ExecutionRepository executionRepository,
            QuantityRepository quantityRepository, OrderRepository orderRepository,
            ApsTransactionOperations transactions)
    {
        PlanRepository.PlanJob job = published.jobs().stream()
                .filter(value -> "NORMAL".equals(value.jobType()))
                .filter(value -> published.allocations().stream().anyMatch(allocation ->
                        published.segments().stream().anyMatch(segment -> segment.jobId().equals(value.id())
                                && segment.id().equals(allocation.segmentId())
                                && allocation.resourceId().equals(plannedResourceId))))
                .max(java.util.Comparator.comparing(PlanRepository.PlanJob::plannedQty)).orElseThrow();
        String requestId = UUID.nameUUIDFromBytes("mysql-execution-run".getBytes()).toString();
        var create = new ExecutionLifecycleService.CreateRun(requestId, published.plan().id(), job.id(),
                job.plannedQty(), job.uomCode());
        var created = service.create(scope, create, actor.username());
        assertThat(created.reused()).isFalse();
        assertThat(service.create(scope, create, actor.username()).reused()).isTrue();
        assertThat(created.run().status().name()).isEqualTo("READY");

        Instant start = Instant.parse("2026-09-14T00:01:00Z");
        var running = service.transition(scope, new ExecutionLifecycleService.Transition(created.run().id(),
                ExecutionAction.START, 0, start, null), actor.username());
        assertThat(running.status().name()).isEqualTo("RUNNING");
        assertThat(service.detail(scope, running.id()).occupancies()).isNotEmpty()
                .allMatch(value -> value.endAt() == null && value.status().name().equals("ACTIVE"));

        var paused = service.transition(scope, new ExecutionLifecycleService.Transition(running.id(),
                ExecutionAction.PAUSE, running.rowVersion(), start.plusSeconds(60), "换班暂停"), actor.username());
        assertThat(paused.status().name()).isEqualTo("PAUSED");
        var resumed = service.transition(scope, new ExecutionLifecycleService.Transition(paused.id(),
                ExecutionAction.RESUME, paused.rowVersion(), start.plusSeconds(120), null), actor.username());
        assertThat(resumed.status().name()).isEqualTo("RUNNING");

        Resource replacement = resources.saveResource(scope, actor, new Resource(null, workshopId, centerId,
                "MACHINE-EXEC-REPLACEMENT", "执行替换设备", ResourceType.MACHINE, null, null, BigDecimal.ONE,
                "COUNT", "ACTIVE", null, 0));
        addWindow(resources, scope, actor, replacement.id(), AvailabilityType.AVAILABLE,
                Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"));
        String changeId = UUID.nameUUIDFromBytes("mysql-execution-resource-change".getBytes()).toString();
        var change = new ExecutionLifecycleService.ResourceChange(changeId, resumed.id(), resumed.rowVersion(),
                plannedResourceId, replacement.id(), null, OccupancyActivityType.RUN, start.plusSeconds(180),
                "设备切换");
        var changed = service.changeResource(scope, change, actor.username());
        assertThat(changed.reused()).isFalse();
        assertThat(service.changeResource(scope, change, actor.username()).reused()).isTrue();
        var changedRun = changed.run();
        assertThat(service.detail(scope, changedRun.id()).occupancies())
                .filteredOn(value -> value.resourceId().equals(replacement.id()) && value.endAt() == null)
                .singleElement().satisfies(value -> {
                    assertThat(value.sourcePlanAllocationId()).isNotNull();
                    assertThat(value.planSegmentId()).isNotNull();
                });
        assertThat(executionRepository.listPlanningOccupancies(
                executionRepository.listJobTaskIds(changedRun.planJobId()), start.plusSeconds(190),
                start.plusSeconds(3600))).isNotEmpty().allSatisfy(value -> {
                    assertThat(value.resourceId()).isEqualTo(replacement.id());
                    assertThat(value.sourceRequirementId()).isNotNull();
                    assertThat(value.releaseConfidence()).isEqualTo(SolverInput.ReleaseConfidence.TRUSTED);
                });
        assertThatThrownBy(() -> service.transition(scope, new ExecutionLifecycleService.Transition(changedRun.id(),
                ExecutionAction.CANCEL, changedRun.rowVersion(), start.plusSeconds(210), "禁止直接取消"),
                actor.username())).isInstanceOf(ApsBusinessException.class)
                .satisfies(error -> assertThat(((ApsBusinessException) error).errorCode())
                        .isEqualTo(com.ruoyi.aps.application.foundation.ApsErrorCode.INVALID_EXECUTION_TRANSITION));
        var pausedAgain = service.transition(scope, new ExecutionLifecycleService.Transition(changedRun.id(),
                ExecutionAction.PAUSE, changedRun.rowVersion(), start.plusSeconds(240), "任务取消前暂停"),
                actor.username());
        var cancelled = service.transition(scope, new ExecutionLifecycleService.Transition(pausedAgain.id(),
                ExecutionAction.CANCEL, pausedAgain.rowVersion(), start.plusSeconds(300), "未生产取消"),
                actor.username());
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(service.detail(scope, cancelled.id()).executionRevision()).isGreaterThanOrEqualTo(5);

        String reportRunRequest = UUID.nameUUIDFromBytes("mysql-report-run".getBytes()).toString();
        PlanRepository.PlanMember member = job.members().get(0);
        var reportRun = service.create(scope, new ExecutionLifecycleService.CreateRun(reportRunRequest,
                published.plan().id(), job.id(), member.plannedQty(), job.uomCode()), actor.username()).run();
        reportRun = service.transition(scope, new ExecutionLifecycleService.Transition(reportRun.id(),
                ExecutionAction.START, reportRun.rowVersion(), start.plusSeconds(360), null), actor.username());
        BigDecimal partial = member.plannedQty().divide(new BigDecimal("2"));
        String progressId = UUID.nameUUIDFromBytes("mysql-progress-report".getBytes()).toString();
        var progress = new ProductionReportingService.CreateReport(progressId, reportRun.id(),
                reportRun.rowVersion(), member.id(), member.taskId(), ReportType.PROGRESS, start.plusSeconds(420),
                new ProductionReportQuantities(partial, partial, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO), member.uomCode(), null, null);
        ProductionReportingService failing = new ProductionReportingService(executionRepository,
                quantityRepository, transactions, () -> { throw new IllegalStateException("注入报工链中断"); });
        assertThatThrownBy(() -> failing.createReport(scope, progress, actor.username()))
                .isInstanceOf(IllegalStateException.class).hasMessage("注入报工链中断");
        assertThat(reporting.listReports(reportRun.id())).as("M27 写后故障必须回滚报工").isEmpty();
        assertThat(reporting.listOutputLots(reportRun.id())).as("M27 写后故障不能留下孤立 M28").isEmpty();
        var progressResult = reporting.createReport(scope, progress, actor.username());
        assertThat(progressResult.reused()).isFalse();
        assertThat(reporting.createReport(scope, progress, actor.username()).reused()).isTrue();
        assertThat(reporting.listOutputLots(reportRun.id())).singleElement().satisfies(lot -> {
            assertThat(lot.qualityStatus().name()).isEqualTo("RELEASED");
            assertThat(lot.availableQty()).isEqualByComparingTo(partial);
        });
        var sourceTask = orderRepository.findTask(member.taskId()).orElseThrow();
        var parentForReplan = orderRepository.findLot(sourceTask.productionLotId()).orElseThrow();
        String replanOrderId = orderRepository.findOrderLine(parentForReplan.orderLineId()).orElseThrow().orderId();
        String targetTaskId = published.jobs().stream().flatMap(value -> value.members().stream())
                .map(PlanRepository.PlanMember::taskId).filter(value -> !value.equals(member.taskId()))
                .findFirst().orElseThrow();
        var materialDemand = new com.ruoyi.aps.application.order.OrderCatalog.MaterialDemand(
                UUID.nameUUIDFromBytes("mysql-execution-material-demand".getBytes()).toString(), targetTaskId, 99,
                parentForReplan.itemId(), member.taskId(), DemandType.TRANSFER, partial,
                member.uomCode(), new BigDecimal("0.02"), "UNAVAILABLE", null, 0);
        orderRepository.insertMaterialDemand(materialDemand, actor.username());
        var releasedLot = reporting.listOutputLots(reportRun.id()).get(0);
        BigDecimal reservedScrapQty = new BigDecimal("0.02");
        String reserveId = UUID.nameUUIDFromBytes("mysql-quantity-reserve".getBytes()).toString();
        var reserveCommand = new ProductionReportingService.MoveQuantity(reserveId,
                progressResult.run().rowVersion(), releasedLot.rowVersion(), materialDemand.id(),
                materialDemand.targetTaskId(), null, QuantityOperation.RESERVE, partial,
                start.plusSeconds(440), "下序预留");
        var reserved = reporting.moveQuantity(scope, releasedLot.id(), reserveCommand, actor.username());
        assertThat(reporting.moveQuantity(scope, releasedLot.id(), reserveCommand, actor.username()).reused())
                .as("相同预留请求重放不得重复扣减可用量").isTrue();
        var reservedLot = reporting.listOutputLots(reportRun.id()).get(0);
        assertThat(reservedLot.reservedQty()).isEqualByComparingTo(partial);
        assertThat(quantityRepository.findMaterialTargetForUpdate(materialDemand.id()).orElseThrow().status())
                .isEqualTo("AVAILABLE");
        String reservedScrapId = UUID.nameUUIDFromBytes("mysql-reserved-scrap".getBytes()).toString();
        var reservedScrap = new ProductionReportingService.DecideQuality(reservedScrapId,
                reserved.run().rowVersion(), reservedLot.rowVersion(), QualityDecision.SCRAP,
                reservedScrapQty, start.plusSeconds(445), "预留后发现批次缺陷", false);
        var afterReservedScrap = reporting.decideQuality(scope, reservedLot.id(), reservedScrap, actor.username());
        assertThat(reporting.decideQuality(scope, reservedLot.id(), reservedScrap, actor.username()).reused())
                .as("预留报废重放不得重复释放需求分配").isTrue();
        var usableRemainder = reporting.listOutputLots(reportRun.id()).get(0);
        assertThat(usableRemainder.qualityStatus().name()).as("部分报废不能关闭仍有可用量的产出批")
                .isEqualTo("RELEASED");
        assertThat(usableRemainder.reservedQty()).isEqualByComparingTo(partial.subtract(reservedScrapQty));
        assertThat(usableRemainder.availableQty()).isZero();
        assertThat(quantityRepository.findMaterialTargetForUpdate(materialDemand.id()).orElseThrow().status())
                .as("报废预留量后必须同步扣减 M18 分配").isEqualTo("PARTIAL");
        assertThat(quantityRepository.allocatedForDemand(materialDemand.id()))
                .isEqualByComparingTo(partial.subtract(reservedScrapQty));
        CompileRequest replanRequest = compileRequest(workshopId, replanOrderId, "mysql-execution-facts");
        replanRequest = new CompileRequest(replanRequest.requestId(), replanRequest.planVersionId(),
                replanRequest.siteCode(), replanRequest.workshopIds(), replanRequest.orderIds(),
                start.plusSeconds(450), replanRequest.definitionRevision(), 0,
                replanRequest.horizonStartAt(), replanRequest.detailEndAt(), replanRequest.horizonEndAt(),
                start.plusSeconds(450), replanRequest.timeUnitSeconds(), replanRequest.displayTimeZone(),
                replanRequest.modelVersion(), replanRequest.maxSolveSeconds(), replanRequest.randomSeed(),
                replanRequest.solverSearchThreads(), replanRequest.absoluteGapLimit(),
                replanRequest.relativeGapLimit(), replanRequest.sharedBatches(), replanRequest.baseVersion());
        var replanFacts = compiler.compile(scope, replanRequest).input();
        BigDecimal remaining = member.plannedQty().subtract(partial);
        assertThat(replanFacts.executionRevision()).isEqualTo(executionRepository.currentExecutionRevision())
                .isGreaterThan(0);
        assertThat(replanFacts.actualOccupancies()).isNotEmpty()
                .allMatch(value -> value.releaseConfidence().name().equals("TRUSTED"));
        assertThat(replanFacts.tasks()).filteredOn(value -> value.taskId().equals(member.taskId()))
                .singleElement().satisfies(value -> assertThat(value.quantity())
                        .isEqualTo(remaining.stripTrailingZeros().toPlainString()));
        SolverResult carryResult = new CpSatFiniteCapacitySolver().solve(replanFacts, start.plusSeconds(451));
        assertThat(carryResult.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        String activeRunId = reportRun.id();
        assertThat(carryResult.candidate().jobs()).filteredOn(value -> "CARRY".equals(value.jobType())
                && value.memberTaskIds().contains(member.taskId())).singleElement().satisfies(value -> {
                    assertThat(value.carryRunId()).isEqualTo(activeRunId);
                    assertThat(value.plannedQuantity()).isEqualTo(remaining.stripTrailingZeros().toPlainString());
                });
        assertThat(carryResult.candidate().jobs()).noneMatch(value -> !"CARRY".equals(value.jobType())
                && value.memberTaskIds().contains(member.taskId()));
        var createdCarryPlan = planRequests.create(scope, replanRequest, actor.username());
        PlanRepository.ClaimedPlan carryClaim = transactions.required(() -> plans.claimNext(actor.username()))
                .orElseThrow();
        assertThat(carryClaim.plan().id()).isEqualTo(createdCarryPlan.plan().id());
        SolverResult persistedCarry = new CpSatFiniteCapacitySolver().solve(carryClaim.input(),
                start.plusSeconds(452));
        assertThat(persistedCarry.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(transactions.required(() -> plans.storeResult(carryClaim, persistedCarry, actor.username())))
                .isEqualTo(1);
        assertThat(workbench.detail(scope, carryClaim.plan().id()).jobs())
                .filteredOn(value -> "CARRY".equals(value.jobType()))
                .singleElement().satisfies(value -> {
                    assertThat(value.carryRunId()).isEqualTo(activeRunId);
                    assertThat(value.members()).singleElement()
                            .satisfies(carryMember -> assertThat(carryMember.taskId()).isEqualTo(member.taskId()));
                    assertThat(value.plannedQty()).isEqualByComparingTo(remaining);
                });
        assertThat(replanFacts.materialSupplies()).noneMatch(value ->
                "EXECUTION_RELEASED".equals(value.supplyType()) && member.taskId().equals(value.sourceTaskId()))
                .as("已全部预留或报废的执行产出不能再次作为自由供应进入求解器");

        String completeId = UUID.nameUUIDFromBytes("mysql-complete-report".getBytes()).toString();
        var completed = reporting.createReport(scope, new ProductionReportingService.CreateReport(completeId,
                reportRun.id(), afterReservedScrap.run().rowVersion(), member.id(), member.taskId(), ReportType.COMPLETE,
                start.plusSeconds(480), new ProductionReportQuantities(remaining, remaining, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), member.uomCode(), null, null),
                actor.username()).run();
        assertThat(completed.status().name()).isEqualTo("COMPLETED");

        var sourceReport = reporting.listReports(reportRun.id()).stream()
                .filter(value -> value.requestId().equals(progressId)).findFirst().orElseThrow();
        BigDecimal correctedGood = partial.subtract(new BigDecimal("0.25"));
        String correctionId = UUID.nameUUIDFromBytes("mysql-report-correction".getBytes()).toString();
        var correction = reporting.correctReport(scope, sourceReport.id(),
                new ProductionReportingService.CorrectReport(correctionId, completed.rowVersion(),
                        sourceReport.rowVersion(), start.plusSeconds(540),
                        new ProductionReportQuantities(partial, correctedGood, new BigDecimal("0.25"),
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), "现场分类更正"),
                actor.username());
        String completedRunId = reportRun.id();
        assertThat(correction.run().status().name()).as("终态 run 的报工冲正不能恢复运行").isEqualTo("COMPLETED");
        assertThat(reporting.correctReport(scope, sourceReport.id(),
                new ProductionReportingService.CorrectReport(correctionId, completed.rowVersion(),
                        sourceReport.rowVersion(), start.plusSeconds(540),
                        new ProductionReportQuantities(partial, correctedGood, new BigDecimal("0.25"),
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), "现场分类更正"),
                actor.username()).reused()).isTrue();
        assertThat(reporting.listOutputLots(completedRunId).stream()
                .filter(value -> value.sourceReportId().equals(sourceReport.id())))
                .allMatch(value -> value.qualityStatus().name().equals("CLOSED"));
        assertThat(quantityRepository.findMaterialTargetForUpdate(materialDemand.id()).orElseThrow().status())
                .as("报工冲正必须同时冲正下游数量分配").isEqualTo("UNAVAILABLE");
        String correctionReportId = reporting.listReports(completedRunId).stream()
                .filter(report -> report.requestId().equals(correctionId)).findFirst().orElseThrow().id();
        var pendingLot = reporting.listOutputLots(completedRunId).stream()
                .filter(value -> value.sourceReportId().equals(
                        correctionReportId))
                .filter(value -> value.totalQty().compareTo(new BigDecimal("0.25")) == 0)
                .findFirst().orElseThrow();
        String qualityId = UUID.nameUUIDFromBytes("mysql-quality-release".getBytes()).toString();
        var qualityCommand = new ProductionReportingService.DecideQuality(qualityId,
                correction.run().rowVersion(), pendingLot.rowVersion(), QualityDecision.RELEASE,
                new BigDecimal("0.05"), start.plusSeconds(600), "质检部分放行", false);
        var quality = reporting.decideQuality(scope, pendingLot.id(), qualityCommand, actor.username());
        assertThat(quality.run().status().name()).isEqualTo("COMPLETED");
        assertThat(reporting.decideQuality(scope, pendingLot.id(), qualityCommand, actor.username()).reused())
                .as("相同质量请求重放不得重复增加可用量").isTrue();
        assertThat(reporting.listOutputLots(completedRunId).stream()
                .filter(value -> value.id().equals(pendingLot.id())).findFirst().orElseThrow().availableQty())
                .isEqualByComparingTo("0.05");

        var afterRelease = reporting.listOutputLots(completedRunId).stream()
                .filter(value -> value.id().equals(pendingLot.id())).findFirst().orElseThrow();
        String reworkId = UUID.nameUUIDFromBytes("mysql-quality-rework".getBytes()).toString();
        var reworkCommand = new ProductionReportingService.DecideQuality(reworkId, quality.run().rowVersion(),
                afterRelease.rowVersion(), QualityDecision.REWORK, new BigDecimal("0.10"),
                start.plusSeconds(660), "尺寸返工", false);
        var rework = reporting.decideQuality(scope, pendingLot.id(), reworkCommand, actor.username());
        assertThat(reporting.decideQuality(scope, pendingLot.id(), reworkCommand, actor.username()).reused())
                .as("返工重放不得重复创建派生批").isTrue();

        var afterRework = reporting.listOutputLots(completedRunId).stream()
                .filter(value -> value.id().equals(pendingLot.id())).findFirst().orElseThrow();
        String replenishId = UUID.nameUUIDFromBytes("mysql-quality-replenish".getBytes()).toString();
        var replenish = reporting.decideQuality(scope, pendingLot.id(),
                new ProductionReportingService.DecideQuality(replenishId, rework.run().rowVersion(),
                        afterRework.rowVersion(), QualityDecision.SCRAP, new BigDecimal("0.10"),
                        start.plusSeconds(720), "报废并补产", true), actor.username());
        assertThat(replenish.run().status().name()).as("派生恢复不得重开终态原 run").isEqualTo("COMPLETED");

        var parent = orderRepository.findLot(pendingLot.productionLotId()).orElseThrow();
        String orderId = orderRepository.findOrderLine(parent.orderLineId()).orElseThrow().orderId();
        var recoveries = orderRepository.listLots(orderId).stream()
                .filter(value -> parent.id().equals(value.parentLotId())).toList();
        assertThat(recoveries).extracting(value -> value.lotType().name())
                .contains("REWORK", "REPLENISH");
        assertThat(recoveries).allSatisfy(value -> {
            assertThat(value.recoveryReason()).isNotBlank();
            assertThat(orderRepository.listTasksByLot(value.id())).isNotEmpty();
        });
    }

    private CompileRequest compileRequest(String workshopId, String orderId, String namespace)
    {
        return compileRequest(workshopId, List.of(orderId), namespace);
    }

    private CompileRequest compileRequest(String workshopId, List<String> orderIds, String namespace)
    {
        return new CompileRequest(UUID.nameUUIDFromBytes((namespace + "-request").getBytes()).toString(),
                UUID.nameUUIDFromBytes((namespace + "-plan").getBytes()).toString(), "SITE_01",
                List.of(workshopId), orderIds, Instant.parse("2026-09-14T00:00:00Z"), 7, 3,
                Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-14T00:00:00Z"),
                60, "Asia/Shanghai", "aps-cpsat-v1", 10, 1, 1, 0, 0);
    }

    private PlanCandidate invalidResourceCandidate(PlanCandidate source)
    {
        List<PlanCandidate.Allocation> allocations = source.allocations().stream().map(value ->
                new PlanCandidate.Allocation(value.allocationId(), value.segmentId(), value.requirementId(),
                        UUID.randomUUID().toString(), value.resourceType(), value.seatNo(), value.capacityUsed())).toList();
        return new PlanCandidate(source.horizonStartAt(), source.horizonEndAt(), source.jobs(), source.segments(),
                allocations, source.futureCarryForwardTaskIds(), source.unplannedTaskIds());
    }

    private void assertPlanCandidateRows(DataSource dataSource, String planVersionId, long jobs, long members,
            long segments, long allocations)
    {
        try (Connection connection = dataSource.getConnection())
        {
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM aps_plan_job WHERE plan_version_id=?", planVersionId))
                    .isEqualTo(jobs);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM aps_plan_job_member m JOIN aps_plan_job j "
                    + "ON j.id=m.plan_job_id WHERE j.plan_version_id=?", planVersionId)).isEqualTo(members);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM aps_plan_segment s JOIN aps_plan_job j "
                    + "ON j.id=s.plan_job_id WHERE j.plan_version_id=?", planVersionId)).isEqualTo(segments);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM aps_plan_allocation a JOIN aps_plan_segment s "
                    + "ON s.id=a.plan_segment_id JOIN aps_plan_job j ON j.id=s.plan_job_id WHERE j.plan_version_id=?",
                    planVersionId)).isEqualTo(allocations);
        }
        catch (SQLException exception)
        {
            throw new AssertionError("读取候选持久化行失败", exception);
        }
    }

    private OperationSpec createActiveOperation(RoutingManagementService service, String centerId, String actor)
    {
        ResourceRequirement requirement = new ResourceRequirement(null, null, 1, ResourceType.MACHINE,
                centerId, null, 1, null, null, null, false, 0);
        OperationPhase phase = new OperationPhase(null, null, 1, PhaseType.RUN, "加工", DurationModel.PER_UNIT,
                0, new BigDecimal("10"), ResourceHoldPolicy.PHASE_ONLY, List.of(requirement), 0);
        OperationSpec draft = service.saveOperation(new OperationSpec(null, "OP-MFG", "标准加工", OperationMode.MAN_MACHINE,
                null, false, false, null, null, null, "DRAFT", null, List.of(phase), 0), actor);
        return service.activateOperation(draft.id(), draft.rowVersion(), actor);
    }

    private OperationSpec createActiveBatchOperation(RoutingManagementService service, String centerId,
            String actor)
    {
        ResourceRequirement requirement = new ResourceRequirement(null, null, 1, ResourceType.MACHINE,
                centerId, null, 1, null, null, null, false, 0);
        OperationPhase phase = new OperationPhase(null, null, 1, PhaseType.RUN, "固定炉次", DurationModel.FIXED,
                600, BigDecimal.ZERO, ResourceHoldPolicy.PHASE_ONLY, List.of(requirement), 0);
        OperationSpec draft = service.saveOperation(new OperationSpec(null, "OP-BATCH-MFG", "固定周期合批",
                OperationMode.BATCH, null, false, false, new BigDecimal("10"), "PCS", null, "DRAFT", null,
                List.of(phase), 0), actor);
        return service.activateOperation(draft.id(), draft.rowVersion(), actor);
    }

    private RouteVersion createAndPublishRoute(RoutingManagementService service, String itemId, String routeCode,
            String version, String operationId, List<String> nodeCodes, List<RouteEdgeDraft> edges, String actor)
    {
        RouteVersion route = service.createRoute(new RouteVersion(null, itemId, routeCode, version, "DRAFT",
                null, null, null, null, null, 0), actor);
        List<RouteNodeDraft> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < nodeCodes.size(); index++)
            nodes.add(node(operationId, nodeCodes.get(index), (index + 1) * 10, index == nodeCodes.size() - 1));
        service.saveGraph(route.id(), new RouteGraphDraft(nodes, edges), actor);
        return service.publishRoute(route.id(), 0, actor);
    }

    private RouteNodeDraft node(String operationId, String code, int order)
    {
        return new RouteNodeDraft(null, operationId, code, code, order, BigDecimal.ONE, false);
    }

    private RouteNodeDraft node(String operationId, String code, int order, boolean terminal)
    {
        return new RouteNodeDraft(null, operationId, code, code, order, BigDecimal.ONE, terminal);
    }

    private OrderLine orderLine(int lineNo, String itemId, String routeId, BigDecimal quantity,
            List<ComponentSpec> components, List<LineDependencySpec> dependencies)
    {
        return new OrderLine(null, null, lineNo, itemId, routeId, quantity, "PCS", null, null,
                "DRAFT", components, dependencies, 0);
    }

    private ProductionOrder order(String orderNo, String source, String externalId, List<OrderLine> lines)
    {
        return new ProductionOrder(null, orderNo, source, externalId, null, null, 50, null, null,
                "DRAFT", null, lines, 0);
    }

    private Resource person(ResourceManagementService service, ResourceAccessScope scope, ApsAuditActor actor,
            String workshopId, String centerId, String code, String name)
    {
        return service.saveResource(scope, actor, new Resource(null, workshopId, centerId, code, name,
                ResourceType.PERSON, null, null, BigDecimal.ONE, "COUNT", "ACTIVE", null, 0));
    }

    private void addSkill(ResourceManagementService service, ResourceAccessScope scope, ApsAuditActor actor,
            String resourceId, String code, int level)
    {
        service.saveSkill(scope, actor, new Skill(null, resourceId, code, code, level, null, null, null, "ACTIVE", 0));
    }

    private void addWindow(ResourceManagementService service, ResourceAccessScope scope, ApsAuditActor actor,
            String resourceId, AvailabilityType type, Instant start, Instant end)
    {
        service.saveAvailability(scope, actor, new Availability(null, resourceId, type, start, end, BigDecimal.ONE,
                "MANUAL", "TEST", null, 0));
    }

    private void verifySchema(Connection connection, String schema) throws SQLException
    {
        Set<String> tables;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? "
                        + "AND table_name LIKE 'aps\\_%' AND table_name <> 'aps_flyway_schema_history'"))
        {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery())
            {
                var result = new java.util.HashSet<String>();
                while (resultSet.next())
                {
                    result.add(resultSet.getString(1).toLowerCase(Locale.ROOT));
                }
                tables = Set.copyOf(result);
            }
        }
        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(tables).doesNotContain("aps_factory");
        assertThat(tables.stream().filter(name -> name.contains("skill")).collect(Collectors.toSet()))
                .containsExactly("aps_resource_skill");
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = ?",
                schema)).isEqualTo(66);
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                        + "AND table_name = 'aps_plan_job' AND column_name = 'carry_run_id'",
                schema)).isEqualTo(1);
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                        + "AND table_name = 'aps_actual_occupancy' "
                        + "AND column_name = 'source_plan_allocation_id'",
                schema)).isEqualTo(1);
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                        + "AND table_name LIKE 'aps\\_%' AND engine <> 'InnoDB'",
                schema)).isZero();
        assertThat(singleLong(connection,
                "SELECT datetime_precision FROM information_schema.columns WHERE table_schema = ? "
                        + "AND table_name = 'aps_workshop' AND column_name = 'created_at'",
                schema)).isEqualTo(3);
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                        + "AND table_name = 'aps_operation_phase' AND column_name IN "
                        + "('max_segments','min_segment_seconds','resume_setup_seconds','segment_resource_policy')",
                schema)).isEqualTo(4);
        assertThat(singleLong(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                        + "AND table_name = 'aps_resource_requirement' AND column_name = 'hold_on_pause'",
                schema)).isEqualTo(1);
    }

    private void verifyConstraintsAndTransactions(Connection connection) throws SQLException
    {
        String workshopId = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO aps_workshop (id, workshop_code, workshop_name) VALUES (?, ?, ?)"))
        {
            insert.setString(1, workshopId);
            insert.setString(2, "WS-001");
            insert.setString(3, "一号车间");
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(() -> execute(connection,
                "INSERT INTO aps_workshop (id, workshop_code, workshop_name) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), "WS-001", "重复编码"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection,
                "INSERT INTO aps_workshop (id, workshop_code, workshop_name, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), "WS-BAD", "非法状态", "BROKEN"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection,
                "INSERT INTO aps_work_center (id, workshop_id, center_code, center_name) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "WC-BAD", "无车间中心"))
                .isInstanceOf(SQLException.class);

        assertThat(execute(connection,
                "UPDATE aps_workshop SET workshop_name = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND row_version = ?",
                "一号车间-新版", workshopId, 0L)).isEqualTo(1);
        assertThat(execute(connection,
                "UPDATE aps_workshop SET workshop_name = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND row_version = ?",
                "过期写入", workshopId, 0L)).isZero();

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        String rolledBackId = UUID.randomUUID().toString();
        try
        {
            execute(connection,
                    "INSERT INTO aps_workshop (id, workshop_code, workshop_name) VALUES (?, ?, ?)",
                    rolledBackId, "WS-ROLLBACK", "回滚车间");
            connection.rollback();
        }
        finally
        {
            connection.setAutoCommit(originalAutoCommit);
        }
        assertThat(countById(connection, rolledBackId)).isZero();

        Instant expected = Instant.parse("2026-09-13T01:02:03.123Z").truncatedTo(ChronoUnit.MILLIS);
        String timeId = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO aps_workshop (id, workshop_code, workshop_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)"))
        {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            insert.setString(1, timeId);
            insert.setString(2, "WS-TIME");
            insert.setString(3, "时间车间");
            insert.setTimestamp(4, Timestamp.from(expected), utc);
            insert.setTimestamp(5, Timestamp.from(expected), utc);
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT created_at FROM aps_workshop WHERE id = ?"))
        {
            select.setString(1, timeId);
            try (ResultSet resultSet = select.executeQuery())
            {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant())
                        .isEqualTo(expected);
            }
        }
    }

    private int execute(Connection connection, String sql, Object... parameters) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            for (int index = 0; index < parameters.length; index++)
            {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        }
    }

    private void backdatePlan(DataSource dataSource, String planVersionId, Instant updatedAt)
    {
        try (Connection connection = dataSource.getConnection())
        {
            assertThat(execute(connection, "UPDATE aps_plan_version SET updated_at=? WHERE id=?",
                    Timestamp.from(updatedAt), planVersionId)).isEqualTo(1);
        }
        catch (SQLException exception)
        {
            throw new IllegalStateException("无法为遗留 SOLVING 恢复测试回拨更新时间", exception);
        }
    }

    private void createPublishFailureTrigger(DataSource dataSource)
    {
        executeDdl(dataSource, "CREATE TRIGGER aps_test_fail_publish BEFORE UPDATE ON aps_plan_version "
                + "FOR EACH ROW BEGIN IF NEW.status='PUBLISHED' AND OLD.status='FEASIBLE' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='simulated publish cutover failure'; END IF; END");
    }

    private void dropPublishFailureTrigger(DataSource dataSource)
    {
        executeDdl(dataSource, "DROP TRIGGER IF EXISTS aps_test_fail_publish");
    }

    private void executeDdl(DataSource dataSource, String sql)
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
        catch (SQLException exception)
        {
            throw new IllegalStateException("发布故障注入 DDL 执行失败", exception);
        }
    }

    private long countById(Connection connection, String id) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM aps_workshop WHERE id = ?"))
        {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery())
            {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long singleLong(Connection connection, String sql, String parameter) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery())
            {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private String schemaUrl(String adminUrl, String schema)
    {
        int query = adminUrl.indexOf('?');
        String suffix = query >= 0 ? adminUrl.substring(query) : "";
        String base = query >= 0 ? adminUrl.substring(0, query) : adminUrl;
        int slash = base.lastIndexOf('/');
        if (slash < "jdbc:mysql://".length())
        {
            throw new IllegalArgumentException("APS_TEST_MYSQL_ADMIN_URL 必须包含数据库路径");
        }
        return base.substring(0, slash + 1) + schema + suffix;
    }

    private String withUtc(String url)
    {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private String envOrDefault(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
