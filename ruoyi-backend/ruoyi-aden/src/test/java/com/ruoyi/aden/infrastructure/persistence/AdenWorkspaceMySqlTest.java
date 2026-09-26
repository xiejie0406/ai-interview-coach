package com.ruoyi.aden.infrastructure.persistence;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.projection.AdenOpaqueCursorCodec;
import com.ruoyi.aden.application.projection.AdenOperatorProjectionRepository;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.event.AdenOutboxMessage;
import com.ruoyi.aden.application.event.AdenOutboxRecoveryPort;
import com.ruoyi.aden.application.event.AdenOutboxRecoveryService;
import com.ruoyi.aden.application.runner.AdenRunnerAdministrationService;
import com.ruoyi.aden.application.runner.AdenRunnerAuthenticationPort;
import com.ruoyi.aden.application.runner.AdenRunnerLedgerRepository;
import com.ruoyi.aden.application.runner.AdenRunnerPepperProvider;
import com.ruoyi.aden.application.runner.AdenRunnerSessionService;
import com.ruoyi.aden.application.runner.AdenRunnerClaimService;
import com.ruoyi.aden.application.runner.AdenRunnerDeliveryRepository;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerReceiptService;
import com.ruoyi.aden.application.runner.AdenRunnerHeartbeatService;
import com.ruoyi.aden.application.runner.AdenRunnerLeaseRecoveryService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.task.AdenSyntheticTaskInput;
import com.ruoyi.aden.application.task.AdenSyntheticTaskValidator;
import com.ruoyi.aden.application.workspace.AdenWorkspaceService;
import com.ruoyi.aden.application.workspace.AdenWorkspaceAccessGuard;
import com.ruoyi.aden.application.error.AdenVersionConflictException;
import com.ruoyi.aden.application.task.AdenTaskCasRepository;
import com.ruoyi.aden.application.task.AdenTaskLedgerRepository;
import com.ruoyi.aden.application.task.AdenTaskResult;
import com.ruoyi.aden.application.task.AdenTaskTransactionService;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskActor;
import com.ruoyi.aden.domain.task.AdenTaskCommand;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskType;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.runner.AdenReceiptType;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenWorkspaceMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenOutboxMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenRunnerDeliveryMapper;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenProjectionMapper;
import com.ruoyi.aden.infrastructure.security.AdenRunnerCredentialCodec;
import com.ruoyi.aden.infrastructure.security.AdenRunnerSessionCodec;
import com.ruoyi.aden.migration.AdenDatabasePreconditions;
import com.ruoyi.aden.migration.AdenFlywayFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenWorkspaceMySqlTest {
    private static final Instant NOW = Instant.parse("2026-09-13T02:00:00.123456Z");
    private static String adminUrl;
    private static String username;
    private static String password;
    private static String databaseName;
    private static JdbcTemplate jdbc;
    private static AdenWorkspaceRepository repository;
    private static AdenTaskCasRepository taskCasRepository;
    private static AdenTaskLedgerRepository taskLedgerRepository;
    private static AdenOutboxRecoveryPort outboxRecoveryPort;
    private static ObjectMapper objectMapper;
    private static AdenRunnerMapper runnerMapper;
    private static AdenRunnerDeliveryMapper runnerDeliveryMapper;
    private static AdenOperatorProjectionRepository projectionRepository;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void prepareDatabaseAndMapper() throws Exception {
        adminUrl = environment("ADEN_TEST_DB_ADMIN_URL");
        Assumptions.assumeTrue(adminUrl != null,
                "未配置本机一次性 MySQL；请运行 ruoyi-aden/scripts/run-local-mysql-tests.ps1");
        username = requiredEnvironment("ADEN_TEST_DB_USERNAME");
        password = System.getenv().getOrDefault("ADEN_TEST_DB_PASSWORD", "");
        databaseName = "aden_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + identifier(databaseName)
                    + " character set utf8mb4 collate utf8mb4_0900_ai_ci");
        }
        DataSource dataSource = new DriverManagerDataSource(withDatabase(adminUrl, databaseName), username, password);
        ResourceDatabasePopulator baseline = new ResourceDatabasePopulator();
        baseline.setSqlScriptEncoding("UTF-8");
        baseline.addScript(new FileSystemResource(
                workspacePath("ruoyi-backend", "sql", "ry_20260417.sql")));
        baseline.execute(dataSource);
        AdenDatabasePreconditions.verify(dataSource, databaseName);
        var flyway = AdenFlywayFactory.create(dataSource);
        flyway.baseline();
        flyway.migrate();
        flyway.validate();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/aden/AdenWorkspaceMapper.xml"),
                new ClassPathResource("mapper/aden/AdenTaskMapper.xml"),
                new ClassPathResource("mapper/aden/AdenOutboxMapper.xml"),
                new ClassPathResource("mapper/aden/AdenRunnerMapper.xml"),
                new ClassPathResource("mapper/aden/AdenRunnerDeliveryMapper.xml"),
                new ClassPathResource("mapper/aden/AdenProjectionMapper.xml"));
        SqlSessionFactory sessionFactory = factoryBean.getObject();
        if (sessionFactory == null) throw new IllegalStateException("无法创建 MyBatis SqlSessionFactory");
        AdenWorkspaceMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(AdenWorkspaceMapper.class);
        repository = new MyBatisAdenWorkspaceRepository(mapper);
        AdenTaskMapper taskMapper = new SqlSessionTemplate(sessionFactory).getMapper(AdenTaskMapper.class);
        taskCasRepository = new MyBatisAdenTaskCasRepository(taskMapper);
        taskLedgerRepository = new MyBatisAdenTaskLedgerRepository(taskMapper);
        AdenOutboxMapper outboxMapper = new SqlSessionTemplate(sessionFactory).getMapper(AdenOutboxMapper.class);
        outboxRecoveryPort = new MyBatisAdenOutboxRecoveryRepository(outboxMapper);
        runnerMapper = new SqlSessionTemplate(sessionFactory).getMapper(AdenRunnerMapper.class);
        runnerDeliveryMapper = new SqlSessionTemplate(sessionFactory).getMapper(AdenRunnerDeliveryMapper.class);
        AdenProjectionMapper projectionMapper = new SqlSessionTemplate(sessionFactory)
                .getMapper(AdenProjectionMapper.class);
        projectionRepository = new MyBatisAdenOperatorProjectionRepository(projectionMapper);
        objectMapper = new ObjectMapper();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (databaseName == null) return;
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("drop database " + identifier(databaseName));
        }
    }

    @Test
    void createAndListRoundTripWorkspaceOwnerAndAudit() {
        AdenWorkspaceService service = service(repository,
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222");
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(42, "operator",
                Set.of(AdenWorkspaceService.CREATE_PERMISSION, AdenWorkspaceService.LIST_PERMISSION));

        AdenWorkspaceMembership created = transactions.execute(status -> service.create(
                principal, "MySQL 工作区", "33333333-3333-4333-8333-333333333333"));

        assertEquals("OWNER", created.role().name());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_workspace where workspace_id = ? and created_by_ruoyi_user_id = 42",
                Integer.class, created.workspace().id().value()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_workspace_member where workspace_id = ? and ruoyi_user_id = 42 "
                        + "and workspace_role = 'OWNER' and member_status = 'ACTIVE'",
                Integer.class, created.workspace().id().value()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_audit_event where workspace_id = ? and action_code = 'WORKSPACE_CREATED' "
                        + "and actor_id = '42'",
                Integer.class, created.workspace().id().value()));
        List<AdenWorkspaceMembership> listed = service.list(principal);
        assertTrue(listed.stream().anyMatch(value -> value.workspace().id().equals(created.workspace().id())
                && value.role().name().equals("OWNER") && value.workspace().createdAt().equals(NOW)));
    }

    @Test
    void auditFailureRollsBackWorkspaceAndOwnerInSameTransaction() {
        String workspaceId = "44444444-4444-4444-8444-444444444444";
        AdenWorkspaceRepository failingAudit = new FailingAuditRepository(repository);
        AdenWorkspaceService service = service(failingAudit, workspaceId,
                "55555555-5555-4555-8555-555555555555");
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(
                43, "operator-rollback", Set.of(AdenWorkspaceService.CREATE_PERMISSION));

        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> service.create(
                principal, "应回滚工作区", "66666666-6666-4666-8666-666666666666")));

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from aden_workspace where workspace_id = ?", Integer.class, workspaceId));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from aden_workspace_member where workspace_id = ?", Integer.class, workspaceId));
    }

    @Test
    void forbiddenAccessAuditIsPlatformScopedAndMinimal() {
        String auditId = "77777777-7777-4777-8777-777777777777";
        String workspaceId = "88888888-8888-4888-8888-888888888888";
        repository.insertWorkspaceForbiddenAudit(new AdenWorkspaceForbiddenAudit(
                auditId, new AdenWorkspaceId(workspaceId), 44,
                "99999999-9999-4999-8999-999999999999", NOW));

        var row = jdbc.queryForMap(
                "select workspace_id, scope_type, action_code, resource_type, resource_id, actor_type, "
                        + "actor_id, outcome, json_length(details_json) details_size "
                        + "from aden_audit_event where audit_event_id = ?", auditId);
        assertEquals(null, row.get("workspace_id"));
        assertEquals("PLATFORM", row.get("scope_type"));
        assertEquals("ADEN_WORKSPACE_FORBIDDEN", row.get("action_code"));
        assertEquals("WORKSPACE", row.get("resource_type"));
        assertEquals(workspaceId, row.get("resource_id"));
        assertEquals("OPERATOR", row.get("actor_type"));
        assertEquals("44", row.get("actor_id"));
        assertEquals("REJECTED", row.get("outcome"));
        assertEquals(0L, ((Number) row.get("details_size")).longValue());
    }

    @Test
    void taskCasUsesWorkspaceTaskVersionAndExpectedStateOnRealMySql() {
        String workspaceId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
        String otherWorkspaceId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
        String taskId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
        insertWorkspace(workspaceId, 45);
        insertWorkspace(otherWorkspaceId, 46);
        insertTask(workspaceId, taskId, "QUEUED", 6, 45);
        insertTask(otherWorkspaceId, taskId, "QUEUED", 6, 46);
        AdenTask previous = persistedTask(workspaceId, taskId, AdenTaskState.QUEUED, 6, 45);
        AdenTask next = previous.transition(
                AdenTaskActor.RUNNER, AdenTaskCommand.START, NOW.plusSeconds(1)).task();

        taskCasRepository.updateState(previous, next);

        assertEquals("RUNNING", jdbc.queryForObject(
                "select task_state from aden_task where workspace_id = ? and task_id = ?",
                String.class, workspaceId, taskId));
        assertEquals(7L, jdbc.queryForObject(
                "select version from aden_task where workspace_id = ? and task_id = ?",
                Long.class, workspaceId, taskId));
        assertEquals("QUEUED", jdbc.queryForObject(
                "select task_state from aden_task where workspace_id = ? and task_id = ?",
                String.class, otherWorkspaceId, taskId));
        assertEquals(6L, jdbc.queryForObject(
                "select version from aden_task where workspace_id = ? and task_id = ?",
                Long.class, otherWorkspaceId, taskId));
        assertThrows(AdenVersionConflictException.class,
                () -> taskCasRepository.updateState(previous, next));
    }

    @Test
    void createSubmitReplayAndValidationFailureAreOneRecoverableLedger() {
        String workspaceId = "10101010-1010-4010-8010-101010101010";
        insertWorkspace(workspaceId, 51);
        insertMember(workspaceId, 51, "OWNER");
        AdenTaskTransactionService service = taskTransactionService(taskLedgerRepository, 1000);
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(51, "task-owner",
                Set.of(AdenTaskTransactionService.CREATE_PERMISSION,
                        AdenTaskTransactionService.COMMAND_PERMISSION,
                        AdenTaskTransactionService.CANCEL_PERMISSION));
        var create = new AdenTaskTransactionService.CreateTask(
                principal, new AdenWorkspaceId(workspaceId), new AdenIdempotencyKey("create:one"),
                "原子合成任务", new AdenSyntheticTaskInput(
                        "fixture:success", "执行确定性合成步骤",
                        AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                new AdenCorrelationId("11111111-aaaa-4111-8111-111111111111"));

        AdenTaskResult created = transactions.execute(status -> service.createTask(create));
        var normalizedReplay = new AdenTaskTransactionService.CreateTask(
                principal, create.workspaceId(), create.idempotencyKey(), "  原子合成任务  ",
                create.input(), create.correlationId());
        AdenTaskResult createReplay = transactions.execute(status -> service.createTask(normalizedReplay));

        assertEquals(AdenTaskState.DRAFT, created.state());
        assertEquals(1, created.version().value());
        assertEquals(false, created.replayed());
        assertEquals(true, createReplay.replayed());
        assertEquals(created.taskId(), createReplay.taskId());
        assertEquals(1, count("aden_task", workspaceId));
        assertEquals(1, count("aden_task_step", workspaceId));
        assertEquals(1, count("aden_event", workspaceId));
        assertEquals(1, count("aden_outbox", workspaceId));
        assertEquals(1, count("aden_inbox", workspaceId));
        assertEquals(1, count("aden_audit_event", workspaceId));
        assertEquals(1L, workspaceSequence(workspaceId));

        var differentCreate = new AdenTaskTransactionService.CreateTask(
                principal, new AdenWorkspaceId(workspaceId), new AdenIdempotencyKey("create:one"),
                "不同标题", create.input(),
                new AdenCorrelationId("22222222-aaaa-4222-8222-222222222222"));
        assertThrows(com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException.class,
                () -> transactions.execute(status -> service.createTask(differentCreate)));

        var submit = new AdenTaskTransactionService.SubmitForValidation(
                principal, new AdenWorkspaceId(workspaceId), created.taskId(), new AdenTaskVersion(1),
                new AdenIdempotencyKey("submit:one"),
                new AdenCorrelationId("33333333-aaaa-4333-8333-333333333333"));
        AdenTaskResult queued = transactions.execute(status -> service.submitForValidation(submit));
        AdenTaskResult submitReplay = transactions.execute(status -> service.submitForValidation(submit));

        assertEquals(AdenTaskState.QUEUED, queued.state());
        assertEquals(3, queued.version().value());
        assertEquals(true, submitReplay.replayed());
        assertEquals(3, count("aden_event", workspaceId));
        assertEquals(3, count("aden_outbox", workspaceId));
        assertEquals(2, count("aden_inbox", workspaceId));
        assertEquals(2, count("aden_audit_event", workspaceId));
        assertEquals(3L, workspaceSequence(workspaceId));
        assertEquals("READY", jdbc.queryForObject(
                "select step_state from aden_task_step where workspace_id = ? and task_id = ?",
                String.class, workspaceId, created.taskId().value()));
        assertEquals(1, count("aden_runner_delivery", workspaceId));
        var delivery = jdbc.queryForMap(
                "select delivery_state, attempt_no, fence_token, task_package_json, task_package_hash "
                        + "from aden_runner_delivery where workspace_id=? and task_id=?",
                workspaceId, created.taskId().value());
        assertEquals("READY", delivery.get("delivery_state"));
        assertEquals(1, ((Number) delivery.get("attempt_no")).intValue());
        assertEquals(0L, ((Number) delivery.get("fence_token")).longValue());
        assertTrue(delivery.get("task_package_json").toString().contains(
                delivery.get("task_package_hash").toString()));
        assertEquals(List.of(1L, 2L, 3L), jdbc.queryForList(
                "select event_seq from aden_event where workspace_id = ? order by event_seq",
                Long.class, workspaceId));
        assertEquals(List.of(1L, 2L, 3L), jdbc.queryForList(
                "select aggregate_version from aden_event where workspace_id = ? order by event_seq",
                Long.class, workspaceId));

        var cancel = new AdenTaskTransactionService.RequestCancel(
                principal, new AdenWorkspaceId(workspaceId), created.taskId(), new AdenTaskVersion(3),
                new AdenIdempotencyKey("cancel:one"),
                new AdenCorrelationId("99999999-aaaa-4999-8999-999999999999"));
        AdenTaskResult cancelRequested = transactions.execute(status -> service.requestCancel(cancel));
        AdenTaskResult cancelReplay = transactions.execute(status -> service.requestCancel(cancel));
        assertEquals(AdenTaskState.CANCELED, cancelRequested.state());
        assertEquals(5, cancelRequested.version().value());
        assertEquals(true, cancelReplay.replayed());
        assertEquals(5, count("aden_event", workspaceId));
        assertEquals(5, count("aden_outbox", workspaceId));
        assertEquals(3, count("aden_inbox", workspaceId));
        assertEquals(3, count("aden_audit_event", workspaceId));
        assertEquals(5L, workspaceSequence(workspaceId));
        assertEquals("CANCELED", jdbc.queryForObject(
                "select delivery_state from aden_runner_delivery where workspace_id=? and task_id=?",
                String.class, workspaceId, created.taskId().value()));

        var failedCreate = new AdenTaskTransactionService.CreateTask(
                principal, new AdenWorkspaceId(workspaceId), new AdenIdempotencyKey("create:failed"),
                "预期校验失败", new AdenSyntheticTaskInput(
                        "fixture:validation-fail", "触发合成校验失败",
                        AdenSyntheticTaskInput.ExpectedOutcome.FAIL_VALIDATION),
                new AdenCorrelationId("44444444-aaaa-4444-8444-444444444444"));
        AdenTaskResult failedDraft = transactions.execute(status -> service.createTask(failedCreate));
        var failedSubmit = new AdenTaskTransactionService.SubmitForValidation(
                principal, new AdenWorkspaceId(workspaceId), failedDraft.taskId(), new AdenTaskVersion(1),
                new AdenIdempotencyKey("submit:failed"),
                new AdenCorrelationId("55555555-aaaa-4555-8555-555555555555"));
        AdenTaskResult failed = transactions.execute(status -> service.submitForValidation(failedSubmit));

        assertEquals(AdenTaskState.FAILED, failed.state());
        assertEquals(3, failed.version().value());
        assertEquals("SYNTHETIC_VALIDATION_REJECTED", failed.reasonCode());
        assertEquals("SYNTHETIC_VALIDATION_REJECTED", jdbc.queryForObject(
                "select error_code from aden_task where workspace_id = ? and task_id = ?",
                String.class, workspaceId, failed.taskId().value()));
        assertEquals("PENDING", jdbc.queryForObject(
                "select step_state from aden_task_step where workspace_id = ? and task_id = ?",
                String.class, workspaceId, failed.taskId().value()));
    }

    @Test
    void auditFailureRollsBackTaskStepEventOutboxInboxAndWorkspaceSequence() {
        String workspaceId = "20202020-2020-4020-8020-202020202020";
        insertWorkspace(workspaceId, 52);
        insertMember(workspaceId, 52, "OWNER");
        AdenTaskLedgerRepository failingLedger = failingAuditLedger(taskLedgerRepository);
        AdenTaskTransactionService service = taskTransactionService(failingLedger, 2000);
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(
                52, "rollback-owner", Set.of(AdenTaskTransactionService.CREATE_PERMISSION));
        var create = new AdenTaskTransactionService.CreateTask(
                principal, new AdenWorkspaceId(workspaceId), new AdenIdempotencyKey("create:rollback"),
                "必须整体回滚", new AdenSyntheticTaskInput(
                        "fixture:rollback", "审计失败时回滚",
                        AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                new AdenCorrelationId("66666666-aaaa-4666-8666-666666666666"));

        assertThrows(RuntimeException.class,
                () -> transactions.execute(status -> service.createTask(create)));

        assertEquals(0, count("aden_task", workspaceId));
        assertEquals(0, count("aden_task_step", workspaceId));
        assertEquals(0, count("aden_event", workspaceId));
        assertEquals(0, count("aden_outbox", workspaceId));
        assertEquals(0, count("aden_runner_delivery", workspaceId));
        assertEquals(0, count("aden_inbox", workspaceId));
        assertEquals(0, count("aden_audit_event", workspaceId));
        assertEquals(0L, workspaceSequence(workspaceId));
    }

    @Test
    void outboxClaimRetryDeadAndExpiredClaimRecoveryAreFencedOnRealMySql() {
        String workspaceId = "30303030-3030-4030-8030-303030303030";
        insertWorkspace(workspaceId, 53);
        insertMember(workspaceId, 53, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 3000);
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(53, "outbox-owner",
                Set.of(AdenTaskTransactionService.CREATE_PERMISSION,
                        AdenTaskTransactionService.COMMAND_PERMISSION));
        AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                new AdenTaskTransactionService.CreateTask(
                        principal, new AdenWorkspaceId(workspaceId), new AdenIdempotencyKey("create:outbox"),
                        "Outbox 恢复", new AdenSyntheticTaskInput(
                        "fixture:outbox", "产生三条事件", AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                        new AdenCorrelationId("77777777-aaaa-4777-8777-777777777777"))));
        transactions.execute(status -> taskService.submitForValidation(
                new AdenTaskTransactionService.SubmitForValidation(
                        principal, new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                        new AdenIdempotencyKey("submit:outbox"),
                        new AdenCorrelationId("88888888-aaaa-4888-8888-888888888888"))));
        MutableClock clock = new MutableClock(NOW);
        AtomicLong tokens = new AtomicLong();
        AdenOutboxRecoveryService recovery = new AdenOutboxRecoveryService(
                outboxRecoveryPort, () -> String.format(
                "99999999-0000-4000-8000-%012d", tokens.incrementAndGet()),
                clock, 30, 100, 800, 2);

        List<AdenOutboxMessage> first = transactions.execute(status -> recovery.claim(3));
        assertEquals(3, first.size());
        transactions.executeWithoutResult(status -> recovery.acknowledge(first.get(0)));
        transactions.executeWithoutResult(status -> recovery.fail(
                first.get(1), new IllegalStateException("temporary")));

        clock.advance(Duration.ofSeconds(31));
        List<AdenOutboxMessage> reclaimed = transactions.execute(status -> recovery.claim(3));
        assertEquals(2, reclaimed.size());
        assertTrue(reclaimed.stream().allMatch(message -> message.attempts() == 2));
        AdenOutboxMessage retry = reclaimed.stream()
                .filter(message -> message.outboxId().equals(first.get(1).outboxId())).findFirst().orElseThrow();
        AdenOutboxMessage expired = reclaimed.stream()
                .filter(message -> message.outboxId().equals(first.get(2).outboxId())).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(
                status -> outboxRecoveryPort.markPublished(
                        first.get(2).workspaceId(), first.get(2).outboxId(),
                        first.get(2).claimToken(), clock.instant())));
        transactions.executeWithoutResult(status -> recovery.fail(
                retry, new IllegalStateException("permanent")));
        transactions.executeWithoutResult(status -> recovery.acknowledge(expired));

        assertEquals(2, jdbc.queryForObject(
                "select count(*) from aden_outbox where workspace_id = ? and outbox_state = 'PUBLISHED'",
                Integer.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_outbox where workspace_id = ? and outbox_state = 'DEAD' "
                        + "and attempts = 2 and claim_token is null and claimed_until is null "
                        + "and last_error like 'IllegalStateException:%'",
                Integer.class, workspaceId));
    }

    @Test
    void concurrentCommandsInOneWorkspaceCommitContiguousEventSequence() throws Exception {
        String workspaceId = "40404040-4040-4040-8040-404040404040";
        insertWorkspace(workspaceId, 54);
        insertMember(workspaceId, 54, "OWNER");
        AdenTaskTransactionService service = taskTransactionService(taskLedgerRepository, 4000);
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(
                54, "concurrent-owner", Set.of(AdenTaskTransactionService.CREATE_PERMISSION));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<AdenTaskResult>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                int taskNo = index;
                futures.add(workers.submit(() -> {
                    start.await();
                    return transactions.execute(status -> service.createTask(
                            new AdenTaskTransactionService.CreateTask(
                                    principal, new AdenWorkspaceId(workspaceId),
                                    new AdenIdempotencyKey("create:concurrent:" + taskNo),
                                    "并发任务 " + taskNo,
                                    new AdenSyntheticTaskInput(
                                            "fixture:concurrent-" + taskNo, "并发事件序号",
                                            AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                                    new AdenCorrelationId(String.format(
                                            "aaaaaaaa-bbbb-4ccc-8ddd-%012d", taskNo + 1)))));
                }));
            }
            start.countDown();
            Set<String> taskIds = new HashSet<>();
            for (Future<AdenTaskResult> future : futures) {
                taskIds.add(future.get(30, TimeUnit.SECONDS).taskId().value());
            }
            assertEquals(4, taskIds.size());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(List.of(1L, 2L, 3L, 4L), jdbc.queryForList(
                "select event_seq from aden_event where workspace_id = ? order by event_seq",
                Long.class, workspaceId));
        assertEquals(4L, workspaceSequence(workspaceId));
        assertEquals(4, count("aden_task", workspaceId));
        assertEquals(4, count("aden_outbox", workspaceId));
    }

    @Test
    void enrollSessionReplacementAndRevokePersistNoPlaintextSecret() {
        String workspaceId = "50505050-5050-4050-8050-505050505050";
        insertWorkspace(workspaceId, 55);
        insertMember(workspaceId, 55, "OWNER");
        byte[] pepper = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AdenRunnerPepperProvider peppers = new AdenRunnerPepperProvider() {
            @Override public String currentKeyId() { return "test-pepper-v1"; }
            @Override public byte[] pepper(String keyId) {
                if (!currentKeyId().equals(keyId)) throw new IllegalStateException("unknown pepper");
                return pepper.clone();
            }
        };
        AdenRunnerCredentialCodec credentialCodec = new AdenRunnerCredentialCodec(new SecureRandom());
        AdenRunnerSessionCodec sessionCodec = new AdenRunnerSessionCodec(new SecureRandom());
        AdenRunnerLedgerRepository runnerLedger = new MyBatisAdenRunnerLedgerRepository(runnerMapper, objectMapper);
        AdenRunnerAuthenticationPort authentication = new MyBatisAdenRunnerAuthenticationRepository(
                runnerMapper, credentialCodec, sessionCodec, peppers);
        AtomicLong sequence = new AtomicLong(5000);
        AdenIdGenerator generator = () -> String.format(
                "00000000-0000-4000-8000-%012d", sequence.incrementAndGet());
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(repository, (principal, requested, correlation) -> { });
        AdenRequestFingerprint json = new AdenRequestFingerprint(objectMapper);
        AdenRunnerAdministrationService administration = new AdenRunnerAdministrationService(
                runnerLedger, guard, credentialCodec, peppers, json, generator,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));
        AdenRunnerSessionService sessions = new AdenRunnerSessionService(
                runnerLedger, sessionCodec, peppers, json, generator,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(55, "runner-admin", Set.of(
                AdenRunnerAdministrationService.ENROLL_PERMISSION,
                AdenRunnerAdministrationService.REVOKE_PERMISSION));

        var enrollment = transactions.execute(status -> administration.enroll(
                new AdenRunnerAdministrationService.Enroll(principal, new AdenWorkspaceId(workspaceId),
                        "合成 Runner", Set.of(AdenCapabilityCode.CORE),
                        new AdenCorrelationId("bbbbbbbb-aaaa-4bbb-8bbb-000000000001"))));
        var credentialPrincipal = authentication.authenticateCredential(enrollment.credentialToken(), NOW).orElseThrow();
        assertEquals(enrollment.credentialId(), credentialPrincipal.credentialId());
        String storedDigest = jdbc.queryForObject(
                "select credential_keyed_digest from aden_runner_credential where workspace_id=? and credential_id=?",
                String.class, workspaceId, enrollment.credentialId().value());
        assertTrue(storedDigest.matches("[a-f0-9]{64}"));
        assertEquals(false, enrollment.credentialToken().contains(storedDigest));

        var request = new AdenRunnerSessionService.CreateSession(
                1, "0.1.0", 2, Set.of(AdenCapabilityCode.CORE));
        var first = transactions.execute(status -> sessions.exchange(
                credentialPrincipal, request, "bbbbbbbb-aaaa-4bbb-8bbb-000000000002"));
        assertTrue(authentication.authenticateSession(first.sessionToken(), NOW).isPresent());
        var second = transactions.execute(status -> sessions.exchange(
                credentialPrincipal, request, "bbbbbbbb-aaaa-4bbb-8bbb-000000000003"));
        assertEquals(2, second.sessionEpoch());
        assertTrue(authentication.authenticateSession(first.sessionToken(), NOW).isEmpty());
        assertTrue(authentication.authenticateSession(second.sessionToken(), NOW).isPresent());
        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "select session_status from aden_runner_session where workspace_id=? and session_id=?",
                String.class, workspaceId, first.sessionId().value()));

        transactions.executeWithoutResult(status -> administration.revoke(
                new AdenRunnerAdministrationService.Revoke(principal, new AdenWorkspaceId(workspaceId),
                        enrollment.runnerId(),
                        new AdenCorrelationId("bbbbbbbb-aaaa-4bbb-8bbb-000000000004"))));
        assertTrue(authentication.authenticateCredential(enrollment.credentialToken(), NOW).isEmpty());
        assertTrue(authentication.authenticateSession(second.sessionToken(), NOW).isEmpty());
        assertEquals("QUARANTINED", jdbc.queryForObject(
                "select runner_presence from aden_runner where workspace_id=? and runner_id=?",
                String.class, workspaceId, enrollment.runnerId().value()));
        assertEquals(4L, workspaceSequence(workspaceId));
        assertEquals(4, count("aden_event", workspaceId));
        assertEquals(4, count("aden_outbox", workspaceId));
        assertEquals(4, count("aden_audit_event", workspaceId));
    }

    @Test
    void concurrentSessionExchangeLeavesOnlyHighestEpochActiveAndSupportsRetryAfterLostResponse()
            throws Exception {
        String workspaceId = "51515151-5151-4151-8151-515151515151";
        insertWorkspace(workspaceId, 59);
        insertMember(workspaceId, 59, "OWNER");
        byte[] pepper = "fedcba9876543210fedcba9876543210"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AdenRunnerPepperProvider peppers = new AdenRunnerPepperProvider() {
            @Override public String currentKeyId() { return "test-pepper-v1"; }
            @Override public byte[] pepper(String keyId) {
                if (!currentKeyId().equals(keyId)) throw new IllegalStateException("unknown pepper");
                return pepper.clone();
            }
        };
        AdenRunnerCredentialCodec credentialCodec = new AdenRunnerCredentialCodec(new SecureRandom());
        AdenRunnerSessionCodec sessionCodec = new AdenRunnerSessionCodec(new SecureRandom());
        AdenRunnerLedgerRepository runnerLedger = new MyBatisAdenRunnerLedgerRepository(runnerMapper, objectMapper);
        AdenRunnerAuthenticationPort authentication = new MyBatisAdenRunnerAuthenticationRepository(
                runnerMapper, credentialCodec, sessionCodec, peppers);
        AtomicLong sequence = new AtomicLong(13000);
        AdenIdGenerator generator = () -> String.format(
                "00000000-0000-4000-8000-%012d", sequence.incrementAndGet());
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                repository, (principal, requested, correlation) -> { });
        AdenRequestFingerprint json = new AdenRequestFingerprint(objectMapper);
        AdenRunnerAdministrationService administration = new AdenRunnerAdministrationService(
                runnerLedger, guard, credentialCodec, peppers, json, generator,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));
        AdenRunnerSessionService sessions = new AdenRunnerSessionService(
                runnerLedger, sessionCodec, peppers, json, generator,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(59, "session-race-owner", Set.of(
                AdenRunnerAdministrationService.ENROLL_PERMISSION,
                AdenRunnerAdministrationService.REVOKE_PERMISSION));
        var enrollment = transactions.execute(status -> administration.enroll(
                new AdenRunnerAdministrationService.Enroll(operator, new AdenWorkspaceId(workspaceId),
                        "并发 Session Runner", Set.of(AdenCapabilityCode.CORE),
                        new AdenCorrelationId("aaaaaaaa-5151-4151-8151-000000000001"))));
        var credential = authentication.authenticateCredential(enrollment.credentialToken(), NOW).orElseThrow();
        var request = new AdenRunnerSessionService.CreateSession(
                1, "0.1.0", 2, Set.of(AdenCapabilityCode.CORE));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<AdenRunnerSessionService.SessionExchange> exchanges = new ArrayList<>();
        try {
            Future<AdenRunnerSessionService.SessionExchange> first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> sessions.exchange(
                        credential, request, "bbbbbbbb-5151-4151-8151-000000000001"));
            });
            Future<AdenRunnerSessionService.SessionExchange> second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> sessions.exchange(
                        credential, request, "bbbbbbbb-5151-4151-8151-000000000002"));
            });
            start.countDown();
            exchanges.add(first.get(20, TimeUnit.SECONDS));
            exchanges.add(second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(Set.of(1L, 2L), exchanges.stream()
                .map(AdenRunnerSessionService.SessionExchange::sessionEpoch).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1L, exchanges.stream()
                .filter(exchange -> authentication.authenticateSession(exchange.sessionToken(), NOW).isPresent())
                .count());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_session where workspace_id=? and session_status='ACTIVE'",
                Integer.class, workspaceId));
        assertEquals(2L, jdbc.queryForObject(
                "select current_session_epoch from aden_runner where workspace_id=? and runner_id=?",
                Long.class, workspaceId, enrollment.runnerId().value()));

        var lostResponse = transactions.execute(status -> sessions.exchange(
                credential, request, "bbbbbbbb-5151-4151-8151-000000000003"));
        var retriedExchange = transactions.execute(status -> sessions.exchange(
                credential, request, "bbbbbbbb-5151-4151-8151-000000000004"));
        assertEquals(4L, retriedExchange.sessionEpoch());
        assertTrue(authentication.authenticateSession(lostResponse.sessionToken(), NOW).isEmpty());
        assertTrue(authentication.authenticateSession(retriedExchange.sessionToken(), NOW).isPresent());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_session where workspace_id=? and session_status='ACTIVE'",
                Integer.class, workspaceId));
        assertEquals(5L, workspaceSequence(workspaceId));
    }

    @Test
    void taskPackageClaimAndReplayUseOneFencedMySqlLedger() {
        String workspaceId = "60606060-6060-4060-8060-606060606060";
        String runnerId = "61616161-6161-4161-8161-616161616161";
        String credentialId = "62626262-6262-4262-8262-626262626262";
        String sessionId = "63636363-6363-4363-8363-636363636363";
        insertWorkspace(workspaceId, 56);
        insertMember(workspaceId, 56, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 6000);
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(56, "claim-owner", Set.of(
                AdenTaskTransactionService.CREATE_PERMISSION,
                AdenTaskTransactionService.COMMAND_PERMISSION,
                AdenTaskTransactionService.CANCEL_PERMISSION));
        for (int index = 1; index <= 2; index++) {
            int taskNo = index;
            AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                    new AdenTaskTransactionService.CreateTask(operator, new AdenWorkspaceId(workspaceId),
                            new AdenIdempotencyKey("create:claim:" + taskNo), "领取任务 " + taskNo,
                            new AdenSyntheticTaskInput("fixture:claim-" + taskNo, "领取并执行",
                                    AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                            new AdenCorrelationId(String.format(
                                    "aaaaaaaa-6060-4060-8060-%012d", taskNo)))));
            transactions.execute(status -> taskService.submitForValidation(
                    new AdenTaskTransactionService.SubmitForValidation(operator,
                            new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                            new AdenIdempotencyKey("submit:claim:" + taskNo),
                            new AdenCorrelationId(String.format(
                                    "bbbbbbbb-6060-4060-8060-%012d", taskNo)))));
        }
        jdbc.update("update aden_runner_delivery set available_at=utc_timestamp(6) where workspace_id=?",
                workspaceId);
        insertActiveRunnerSession(workspaceId, runnerId, credentialId, sessionId, 1, 1, 56);

        AdenRunnerDeliveryRepository deliveryRepository =
                new MyBatisAdenRunnerDeliveryRepository(runnerDeliveryMapper);
        AtomicLong ids = new AtomicLong(7000);
        AdenRunnerClaimService claimService = new AdenRunnerClaimService(
                deliveryRepository, new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 60, 8, 30, 3600);
        AdenRunnerSessionPrincipal runner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(runnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(sessionId), 1);
        var command = new AdenRunnerClaimService.ClaimCommand(runner,
                new AdenIdempotencyKey("claim:mysql:one"), Set.of(AdenCapabilityCode.CORE), 1, 1);

        var first = transactions.execute(status -> claimService.claim(command));
        var replay = transactions.execute(status -> claimService.claim(command));

        assertEquals(1, first.items().size());
        assertEquals(first.items(), replay.items());
        assertEquals(false, first.replayed());
        assertEquals(true, replay.replayed());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='LEASED' "
                        + "and owner_session_id=? and owner_session_epoch=1 and fence_token=1",
                Integer.class, workspaceId, sessionId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='READY'",
                Integer.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "select inbox_state from aden_inbox where workspace_id=? and consumer='ADEN_RUNNER_CLAIM'",
                String.class, workspaceId));
        assertThrows(com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException.class,
                () -> transactions.execute(status -> claimService.claim(
                        new AdenRunnerClaimService.ClaimCommand(runner,
                                new AdenIdempotencyKey("claim:mysql:one"),
                                Set.of(AdenCapabilityCode.CORE), 1, 2))));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_inbox where workspace_id=? and consumer='ADEN_RUNNER_CLAIM'",
                Integer.class, workspaceId));

        AdenRunnerReceiptService receiptService = new AdenRunnerReceiptService(
                deliveryRepository, taskLedgerRepository, taskCasRepository,
                new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 30, 3600);
        String claimedDeliveryId = first.items().get(0).delivery().deliveryId().value();
        long fence = first.items().get(0).delivery().fenceToken();
        AdenRunnerHeartbeatService heartbeatService = new AdenRunnerHeartbeatService(
                deliveryRepository, 900, 60);
        var heartbeat = transactions.execute(status -> heartbeatService.heartbeat(
                new AdenRunnerHeartbeatService.HeartbeatCommand(runner, 1,
                        List.of(new AdenRunnerHeartbeatService.DeliveryPulse(
                                new com.ruoyi.aden.domain.runner.AdenDeliveryId(claimedDeliveryId),
                                fence, 1)))));
        assertEquals(1, heartbeat.deliveries().size());
        assertEquals(false, heartbeat.deliveries().get(0).cancelRequested());
        var started = transactions.execute(status -> receiptService.apply(
                receipt(runner, "receipt:mysql:start", claimedDeliveryId, fence, 1,
                        AdenReceiptType.STARTED, "cccccccc-6060-4060-8060-000000000001")));
        var progress = transactions.execute(status -> receiptService.apply(
                receipt(runner, "receipt:mysql:progress", claimedDeliveryId, fence, 2,
                        AdenReceiptType.PROGRESS, "cccccccc-6060-4060-8060-000000000002")));
        var completed = transactions.execute(status -> receiptService.apply(
                receipt(runner, "receipt:mysql:complete", claimedDeliveryId, fence, 3,
                        AdenReceiptType.COMPLETED, "cccccccc-6060-4060-8060-000000000003")));
        var completedReplay = transactions.execute(status -> receiptService.apply(
                receipt(runner, "receipt:mysql:complete", claimedDeliveryId, fence, 3,
                        AdenReceiptType.COMPLETED, "cccccccc-6060-4060-8060-000000000003")));

        assertEquals("RUNNING", started.taskState());
        assertEquals("RUNNING", progress.taskState());
        assertEquals("SUCCEEDED", completed.taskState());
        assertEquals(true, completedReplay.replayed());
        assertEquals(6L, completed.taskVersion());
        assertEquals("COMPLETED", jdbc.queryForObject(
                "select delivery_state from aden_runner_delivery where workspace_id=? and delivery_id=?",
                String.class, workspaceId, claimedDeliveryId));
        assertEquals(3L, jdbc.queryForObject(
                "select latest_receipt_sequence from aden_runner_delivery where workspace_id=? and delivery_id=?",
                Long.class, workspaceId, claimedDeliveryId));
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "select step_state from aden_task_step where workspace_id=? and step_id=?",
                String.class, workspaceId, first.items().get(0).delivery().stepId()));
        assertEquals(0, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from aden_inbox where workspace_id=? and consumer='ADEN_RUNNER_RECEIPT'",
                Integer.class, workspaceId));
        assertEquals(9L, workspaceSequence(workspaceId));

        var secondClaim = transactions.execute(status -> claimService.claim(
                new AdenRunnerClaimService.ClaimCommand(runner,
                        new AdenIdempotencyKey("claim:mysql:two"), Set.of(AdenCapabilityCode.CORE), 1, 1)));
        String secondDeliveryId = secondClaim.items().get(0).delivery().deliveryId().value();
        AdenRunnerReceiptService failingReceiptService = new AdenRunnerReceiptService(
                deliveryRepository, failingAuditLedger(taskLedgerRepository), taskCasRepository,
                new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 30, 3600);
        assertThrows(IllegalStateException.class, () -> transactions.execute(status ->
                failingReceiptService.apply(receipt(runner, "receipt:mysql:rollback", secondDeliveryId,
                        secondClaim.items().get(0).delivery().fenceToken(), 1,
                        AdenReceiptType.STARTED, "dddddddd-6060-4060-8060-000000000001"))));
        assertEquals("LEASED", jdbc.queryForObject(
                "select delivery_state from aden_runner_delivery where workspace_id=? and delivery_id=?",
                String.class, workspaceId, secondDeliveryId));
        assertEquals(0L, jdbc.queryForObject(
                "select latest_receipt_sequence from aden_runner_delivery where workspace_id=? and delivery_id=?",
                Long.class, workspaceId, secondDeliveryId));
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from aden_inbox where workspace_id=? and consumer='ADEN_RUNNER_RECEIPT'",
                Integer.class, workspaceId));
        assertEquals(9L, workspaceSequence(workspaceId));

        String secondTaskId = secondClaim.items().get(0).delivery().taskId();
        AdenTaskResult cancelRequested = transactions.execute(status -> taskService.requestCancel(
                new AdenTaskTransactionService.RequestCancel(operator, new AdenWorkspaceId(workspaceId),
                        new AdenTaskId(secondTaskId), new AdenTaskVersion(3),
                        new AdenIdempotencyKey("cancel:active:mysql"),
                        new AdenCorrelationId("eeeeeeee-6060-4060-8060-000000000001"))));
        assertEquals(AdenTaskState.CANCEL_REQUESTED, cancelRequested.state());
        var cancelHeartbeat = transactions.execute(status -> heartbeatService.heartbeat(
                new AdenRunnerHeartbeatService.HeartbeatCommand(runner, 2,
                        List.of(new AdenRunnerHeartbeatService.DeliveryPulse(
                                new com.ruoyi.aden.domain.runner.AdenDeliveryId(secondDeliveryId),
                                secondClaim.items().get(0).delivery().fenceToken(), 1)))));
        assertEquals(true, cancelHeartbeat.deliveries().get(0).cancelRequested());
        var canceled = transactions.execute(status -> receiptService.apply(
                receipt(runner, "receipt:mysql:canceled", secondDeliveryId,
                        secondClaim.items().get(0).delivery().fenceToken(), 1,
                        AdenReceiptType.CANCELED_SAFE_POINT,
                        "ffffffff-6060-4060-8060-000000000001")));
        assertEquals("CANCELED", canceled.taskState());
        assertEquals("CANCELED", jdbc.queryForObject(
                "select delivery_state from aden_runner_delivery where workspace_id=? and delivery_id=?",
                String.class, workspaceId, secondDeliveryId));
        assertEquals(0, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
        assertEquals(11L, workspaceSequence(workspaceId));

    }

    @Test
    void expiredLeasesRequeueRetryOrBecomeOutcomeUnknownByCentralPolicy() throws Exception {
        String workspaceId = "70707070-7070-4070-8070-707070707070";
        String runnerId = "71717171-7171-4171-8171-717171717171";
        String credentialId = "72727272-7272-4272-8272-727272727272";
        String sessionId = "73737373-7373-4373-8373-737373737373";
        insertWorkspace(workspaceId, 57);
        insertMember(workspaceId, 57, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 8000);
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(57, "recovery-owner", Set.of(
                AdenTaskTransactionService.CREATE_PERMISSION,
                AdenTaskTransactionService.COMMAND_PERMISSION,
                AdenTaskTransactionService.CANCEL_PERMISSION));
        for (int index = 1; index <= 3; index++) {
            int taskNo = index;
            AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                    new AdenTaskTransactionService.CreateTask(operator, new AdenWorkspaceId(workspaceId),
                            new AdenIdempotencyKey("create:recovery:" + taskNo), "恢复任务 " + taskNo,
                            new AdenSyntheticTaskInput("fixture:recovery-" + taskNo, "恢复策略",
                                    AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                            new AdenCorrelationId(String.format(
                                    "aaaaaaaa-7070-4070-8070-%012d", taskNo)))));
            transactions.execute(status -> taskService.submitForValidation(
                    new AdenTaskTransactionService.SubmitForValidation(operator,
                            new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                            new AdenIdempotencyKey("submit:recovery:" + taskNo),
                            new AdenCorrelationId(String.format(
                                    "bbbbbbbb-7070-4070-8070-%012d", taskNo)))));
        }
        jdbc.update("update aden_runner_delivery set available_at=utc_timestamp(6) where workspace_id=?",
                workspaceId);
        insertActiveRunnerSession(workspaceId, runnerId, credentialId, sessionId, 1, 3, 57);
        AdenRunnerDeliveryRepository deliveryRepository =
                new MyBatisAdenRunnerDeliveryRepository(runnerDeliveryMapper);
        AtomicLong ids = new AtomicLong(9000);
        AdenRunnerSessionPrincipal runner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(runnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(sessionId), 1);
        AdenRunnerClaimService claimService = new AdenRunnerClaimService(
                deliveryRepository, new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 60, 8, 30, 3600);
        var claimed = transactions.execute(status -> claimService.claim(
                new AdenRunnerClaimService.ClaimCommand(runner,
                        new AdenIdempotencyKey("claim:recovery"), Set.of(AdenCapabilityCode.CORE), 3, 3)));
        assertEquals(3, claimed.items().size());
        var running = claimed.items().get(1).delivery();
        var canceling = claimed.items().get(2).delivery();
        AdenRunnerReceiptService receiptService = new AdenRunnerReceiptService(
                deliveryRepository, taskLedgerRepository, taskCasRepository,
                new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 30, 3600);
        transactions.execute(status -> receiptService.apply(receipt(runner, "receipt:recovery:start",
                running.deliveryId().value(), running.fenceToken(), 1, AdenReceiptType.STARTED,
                "cccccccc-7070-4070-8070-000000000001")));
        transactions.execute(status -> taskService.requestCancel(
                new AdenTaskTransactionService.RequestCancel(operator, new AdenWorkspaceId(workspaceId),
                        new AdenTaskId(canceling.taskId()), new AdenTaskVersion(3),
                        new AdenIdempotencyKey("cancel:recovery"),
                        new AdenCorrelationId("dddddddd-7070-4070-8070-000000000001"))));
        jdbc.update("update aden_runner_delivery set lease_until=timestampadd(second,-1,utc_timestamp(6)) "
                + "where workspace_id=? and delivery_state in ('LEASED','RUNNING')", workspaceId);

        AdenRunnerLeaseRecoveryService recovery = new AdenRunnerLeaseRecoveryService(
                deliveryRepository, taskLedgerRepository, taskCasRepository,
                new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW.plusNanos(100), ZoneOffset.UTC));
        var result = transactions.execute(status -> recovery.recover(new AdenWorkspaceId(workspaceId), 8));

        assertEquals(1, result.requeued());
        assertEquals(1, result.retried());
        assertEquals(1, result.outcomeUnknown());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='READY' "
                        + "and attempt_no=1 and owner_session_id is null and fence_token=1",
                Integer.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='READY' "
                        + "and attempt_no=2 and fence_token=0",
                Integer.class, workspaceId));
        assertRetryPackageHashAndPrecision(jdbc.queryForObject(
                "select task_package_json from aden_runner_delivery where workspace_id=? and attempt_no=2",
                String.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? "
                        + "and delivery_state='OUTCOME_UNKNOWN'",
                Integer.class, workspaceId));
        assertEquals(0, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
        assertEquals("CANCEL_REQUESTED", jdbc.queryForObject(
                "select task_state from aden_task where workspace_id=? and task_id=?",
                String.class, workspaceId, canceling.taskId()));
        assertEquals(14L, workspaceSequence(workspaceId));
    }

    @Test
    void twoRunnerClaimsCompeteWithoutDuplicateDeliveryOnRealMySql() throws Exception {
        String workspaceId = "80808080-8080-4080-8080-808080808080";
        insertWorkspace(workspaceId, 58);
        insertMember(workspaceId, 58, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 10000);
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(58, "concurrent-claim-owner", Set.of(
                AdenTaskTransactionService.CREATE_PERMISSION,
                AdenTaskTransactionService.COMMAND_PERMISSION,
                AdenTaskTransactionService.CANCEL_PERMISSION));
        for (int index = 1; index <= 2; index++) {
            int taskNo = index;
            AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                    new AdenTaskTransactionService.CreateTask(operator, new AdenWorkspaceId(workspaceId),
                            new AdenIdempotencyKey("create:concurrent-claim:" + taskNo),
                            "并发领取任务 " + taskNo,
                            new AdenSyntheticTaskInput("fixture:concurrent-claim-" + taskNo, "并发领取",
                                    AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                            new AdenCorrelationId(String.format(
                                    "aaaaaaaa-8080-4080-8080-%012d", taskNo)))));
            transactions.execute(status -> taskService.submitForValidation(
                    new AdenTaskTransactionService.SubmitForValidation(operator,
                            new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                            new AdenIdempotencyKey("submit:concurrent-claim:" + taskNo),
                            new AdenCorrelationId(String.format(
                                    "bbbbbbbb-8080-4080-8080-%012d", taskNo)))));
        }
        jdbc.update("update aden_runner_delivery set available_at=utc_timestamp(6) where workspace_id=?",
                workspaceId);

        String firstRunnerId = "81818181-8181-4181-8181-818181818181";
        String firstSessionId = "83838383-8383-4383-8383-838383838383";
        String secondRunnerId = "84848484-8484-4484-8484-848484848484";
        String secondSessionId = "86868686-8686-4686-8686-868686868686";
        insertActiveRunnerSession(workspaceId, firstRunnerId,
                "82828282-8282-4282-8282-828282828282", firstSessionId, 1, 1, 58);
        insertActiveRunnerSession(workspaceId, secondRunnerId,
                "85858585-8585-4585-8585-858585858585", secondSessionId, 1, 1, 58);

        AdenRunnerDeliveryRepository deliveryRepository =
                new MyBatisAdenRunnerDeliveryRepository(runnerDeliveryMapper);
        AdenRunnerSessionPrincipal firstRunner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(firstRunnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(firstSessionId), 1);
        AdenRunnerSessionPrincipal secondRunner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(secondRunnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(secondSessionId), 1);
        AdenRunnerClaimService firstClaimService = claimService(deliveryRepository, 11000);
        AdenRunnerClaimService secondClaimService = claimService(deliveryRepository, 12000);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdenRunnerClaimService.ClaimResult> first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> firstClaimService.claim(
                        new AdenRunnerClaimService.ClaimCommand(firstRunner,
                                new AdenIdempotencyKey("claim:concurrent:first"),
                                Set.of(AdenCapabilityCode.CORE), 1, 1)));
            });
            Future<AdenRunnerClaimService.ClaimResult> second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> secondClaimService.claim(
                        new AdenRunnerClaimService.ClaimCommand(secondRunner,
                                new AdenIdempotencyKey("claim:concurrent:second"),
                                Set.of(AdenCapabilityCode.CORE), 1, 1)));
            });
            start.countDown();

            var firstResult = first.get(20, TimeUnit.SECONDS);
            var secondResult = second.get(20, TimeUnit.SECONDS);
            assertEquals(1, firstResult.items().size());
            assertEquals(1, secondResult.items().size());
            assertEquals(2, Set.of(
                    firstResult.items().get(0).delivery().deliveryId().value(),
                    secondResult.items().get(0).delivery().deliveryId().value()).size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(2, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='LEASED'",
                Integer.class, workspaceId));
        assertEquals(2, jdbc.queryForObject(
                "select count(distinct owner_session_id) from aden_runner_delivery where workspace_id=? "
                        + "and delivery_state='LEASED'",
                Integer.class, workspaceId));
        assertEquals(2, jdbc.queryForObject(
                "select coalesce(sum(in_flight),0) from aden_runner_session where workspace_id=?",
                Integer.class, workspaceId));
    }

    @Test
    void concurrentSameClaimRequestReplaysOneCommittedBatch() throws Exception {
        String workspaceId = "90909090-9090-4090-8090-909090909090";
        String runnerId = "91919191-9191-4191-8191-919191919191";
        String sessionId = "93939393-9393-4393-8393-939393939393";
        insertWorkspace(workspaceId, 60);
        insertMember(workspaceId, 60, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 14000);
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(60, "claim-replay-owner", Set.of(
                AdenTaskTransactionService.CREATE_PERMISSION,
                AdenTaskTransactionService.COMMAND_PERMISSION,
                AdenTaskTransactionService.CANCEL_PERMISSION));
        AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                new AdenTaskTransactionService.CreateTask(operator, new AdenWorkspaceId(workspaceId),
                        new AdenIdempotencyKey("create:concurrent-replay"), "并发回放任务",
                        new AdenSyntheticTaskInput("fixture:concurrent-replay", "只领取一次",
                                AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                        new AdenCorrelationId("aaaaaaaa-9090-4090-8090-000000000001"))));
        transactions.execute(status -> taskService.submitForValidation(
                new AdenTaskTransactionService.SubmitForValidation(operator,
                        new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                        new AdenIdempotencyKey("submit:concurrent-replay"),
                        new AdenCorrelationId("bbbbbbbb-9090-4090-8090-000000000001"))));
        jdbc.update("update aden_runner_delivery set available_at=utc_timestamp(6) where workspace_id=?",
                workspaceId);
        insertActiveRunnerSession(workspaceId, runnerId,
                "92929292-9292-4292-8292-929292929292", sessionId, 1, 1, 60);
        AdenRunnerDeliveryRepository deliveryRepository =
                new MyBatisAdenRunnerDeliveryRepository(runnerDeliveryMapper);
        AdenRunnerClaimService claimService = claimService(deliveryRepository, 15000);
        AdenRunnerSessionPrincipal runner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(runnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(sessionId), 1);
        var command = new AdenRunnerClaimService.ClaimCommand(runner,
                new AdenIdempotencyKey("claim:concurrent:same-key"),
                Set.of(AdenCapabilityCode.CORE), 1, 1);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<AdenRunnerClaimService.ClaimResult> results = new ArrayList<>();
        try {
            Future<AdenRunnerClaimService.ClaimResult> first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> claimService.claim(command));
            });
            Future<AdenRunnerClaimService.ClaimResult> second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(status -> claimService.claim(command));
            });
            start.countDown();
            results.add(first.get(20, TimeUnit.SECONDS));
            results.add(second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(Set.of(false, true), results.stream()
                .map(AdenRunnerClaimService.ClaimResult::replayed).collect(java.util.stream.Collectors.toSet()));
        assertEquals(results.get(0).items(), results.get(1).items());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? and delivery_state='LEASED'",
                Integer.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_inbox where workspace_id=? and consumer='ADEN_RUNNER_CLAIM'",
                Integer.class, workspaceId));
        assertEquals(1, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
    }

    @Test
    void retryableReceiptCreatesNextAttemptAndFinalFailureClosesTask() throws Exception {
        String workspaceId = "a0a0a0a0-a0a0-40a0-80a0-a0a0a0a0a0a0";
        String runnerId = "a1a1a1a1-a1a1-41a1-81a1-a1a1a1a1a1a1";
        String sessionId = "a3a3a3a3-a3a3-43a3-83a3-a3a3a3a3a3a3";
        insertWorkspace(workspaceId, 61);
        insertMember(workspaceId, 61, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 16000);
        AdenOperatorPrincipal operator = new AdenOperatorPrincipal(61, "failed-receipt-owner", Set.of(
                AdenTaskTransactionService.CREATE_PERMISSION,
                AdenTaskTransactionService.COMMAND_PERMISSION,
                AdenTaskTransactionService.CANCEL_PERMISSION));
        AdenTaskResult draft = transactions.execute(status -> taskService.createTask(
                new AdenTaskTransactionService.CreateTask(operator, new AdenWorkspaceId(workspaceId),
                        new AdenIdempotencyKey("create:failed-receipt"), "失败回执任务",
                        new AdenSyntheticTaskInput("fixture:failed-receipt", "先重试再终结",
                                AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                        new AdenCorrelationId("aaaaaaaa-a0a0-40a0-80a0-000000000001"))));
        transactions.execute(status -> taskService.submitForValidation(
                new AdenTaskTransactionService.SubmitForValidation(operator,
                        new AdenWorkspaceId(workspaceId), draft.taskId(), new AdenTaskVersion(1),
                        new AdenIdempotencyKey("submit:failed-receipt"),
                        new AdenCorrelationId("bbbbbbbb-a0a0-40a0-80a0-000000000001"))));
        jdbc.update("update aden_runner_delivery set available_at=utc_timestamp(6) where workspace_id=?",
                workspaceId);
        insertActiveRunnerSession(workspaceId, runnerId,
                "a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2", sessionId, 1, 1, 61);
        AdenRunnerDeliveryRepository deliveryRepository =
                new MyBatisAdenRunnerDeliveryRepository(runnerDeliveryMapper);
        AtomicLong ids = new AtomicLong(17000);
        AdenRunnerClaimService claimService = claimService(deliveryRepository, 18000);
        AdenRunnerReceiptService receiptService = new AdenRunnerReceiptService(
                deliveryRepository, taskLedgerRepository, taskCasRepository,
                new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW.plusNanos(100), ZoneOffset.UTC), 30, 3600);
        AdenRunnerSessionPrincipal runner = new AdenRunnerSessionPrincipal(
                new AdenWorkspaceId(workspaceId),
                new com.ruoyi.aden.domain.runner.AdenRunnerId(runnerId),
                new com.ruoyi.aden.domain.runner.AdenSessionId(sessionId), 1);

        var attemptOne = transactions.execute(status -> claimService.claim(
                new AdenRunnerClaimService.ClaimCommand(runner,
                        new AdenIdempotencyKey("claim:failed-receipt:one"),
                        Set.of(AdenCapabilityCode.CORE), 1, 1))).items().get(0).delivery();
        transactions.execute(status -> receiptService.apply(receipt(runner,
                "receipt:failed-receipt:start-one", attemptOne.deliveryId().value(),
                attemptOne.fenceToken(), 1, AdenReceiptType.STARTED,
                "cccccccc-a0a0-40a0-80a0-000000000001")));
        var retryable = transactions.execute(status -> receiptService.apply(receipt(runner,
                "receipt:failed-receipt:retry", attemptOne.deliveryId().value(),
                attemptOne.fenceToken(), 2, AdenReceiptType.FAILED_RETRYABLE,
                "cccccccc-a0a0-40a0-80a0-000000000002")));
        assertEquals("RUNNING", retryable.taskState());
        assertEquals("READY", retryable.stepState());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from aden_runner_delivery where workspace_id=? "
                        + "and attempt_no=2 and delivery_state='READY'",
                Integer.class, workspaceId));
        assertEquals(0, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));

        var attemptTwo = transactions.execute(status -> claimService.claim(
                new AdenRunnerClaimService.ClaimCommand(runner,
                        new AdenIdempotencyKey("claim:failed-receipt:two"),
                        Set.of(AdenCapabilityCode.CORE), 1, 1))).items().get(0).delivery();
        assertEquals(2, attemptTwo.attemptNo());
        assertRetryPackageHashAndPrecision(attemptTwo.taskPackageJson());
        transactions.execute(status -> receiptService.apply(receipt(runner,
                "receipt:failed-receipt:start-two", attemptTwo.deliveryId().value(),
                attemptTwo.fenceToken(), 1, AdenReceiptType.STARTED,
                "dddddddd-a0a0-40a0-80a0-000000000001")));
        var failed = transactions.execute(status -> receiptService.apply(receipt(runner,
                "receipt:failed-receipt:final", attemptTwo.deliveryId().value(),
                attemptTwo.fenceToken(), 2, AdenReceiptType.FAILED_FINAL,
                "dddddddd-a0a0-40a0-80a0-000000000002")));
        assertEquals("FAILED", failed.taskState());
        assertEquals("FAILED", failed.stepState());
        assertEquals("FAILED_FINAL", failed.deliveryState());
        assertEquals(0, jdbc.queryForObject(
                "select in_flight from aden_runner_session where workspace_id=? and session_id=?",
                Integer.class, workspaceId, sessionId));
    }

    private void assertRetryPackageHashAndPrecision(String taskPackageJson) throws Exception {
        var packageBody = (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(taskPackageJson);
        String packageHash = packageBody.remove("packageHash").asText();
        assertEquals("2026-09-13T03:00:00.123456Z", packageBody.get("deadlineAt").asText());
        assertEquals(new AdenRequestFingerprint(objectMapper).hashValue(packageBody), packageHash);
    }

    @Test
    void operatorProjectionPagesBootstrapAuditAndEventsComeFromAuthoritativeMySqlLedger() {
        String workspaceId = "b0b0b0b0-b0b0-40b0-80b0-b0b0b0b0b0b0";
        long userId = 88;
        insertWorkspace(workspaceId, userId);
        insertMember(workspaceId, userId, "OWNER");
        AdenTaskTransactionService taskService = taskTransactionService(taskLedgerRepository, 20000);
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(userId, "projection-owner", Set.of(
                "aden:task:list", "aden:task:query", "aden:task:create", "aden:task:command",
                "aden:task:cancel", "aden:runner:list", "aden:capability:list",
                "aden:audit:list", "aden:event:subscribe"));
        AdenWorkspaceId workspace = new AdenWorkspaceId(workspaceId);
        AdenTaskResult first = transactions.execute(status -> taskService.createTask(
                new AdenTaskTransactionService.CreateTask(principal, workspace,
                        new AdenIdempotencyKey("create:projection:one"), "投影任务一",
                        new AdenSyntheticTaskInput("fixture:projection-one", "合成投影验证",
                                AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                        new AdenCorrelationId("aaaaaaaa-b0b0-40b0-80b0-000000000001"))));
        transactions.execute(status -> taskService.submitForValidation(
                new AdenTaskTransactionService.SubmitForValidation(principal, workspace, first.taskId(),
                        new AdenTaskVersion(1), new AdenIdempotencyKey("submit:projection:one"),
                        new AdenCorrelationId("bbbbbbbb-b0b0-40b0-80b0-000000000001"))));
        transactions.execute(status -> taskService.createTask(
                new AdenTaskTransactionService.CreateTask(principal, workspace,
                        new AdenIdempotencyKey("create:projection:two"), "投影任务二",
                        new AdenSyntheticTaskInput("fixture:projection-two", "合成分页验证",
                                AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED),
                        new AdenCorrelationId("aaaaaaaa-b0b0-40b0-80b0-000000000002"))));
        insertActiveRunnerSession(workspaceId,
                "b1b1b1b1-b1b1-41b1-81b1-b1b1b1b1b1b1",
                "b2b2b2b2-b2b2-42b2-82b2-b2b2b2b2b2b2",
                "b3b3b3b3-b3b3-43b3-83b3-b3b3b3b3b3b3", 1, 1, userId);

        byte[] cursorSecret = new byte[32];
        java.util.Arrays.fill(cursorSecret, (byte) 7);
        AdenOperatorQueryService query = new AdenOperatorQueryService(
                projectionRepository,
                new AdenWorkspaceAccessGuard(repository, (actor, requested, correlation) -> { }),
                new AdenOpaqueCursorCodec(cursorSecret), new AdenRequestFingerprint(objectMapper),
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));
        String correlation = "cccccccc-b0b0-40b0-80b0-000000000001";

        var firstPage = transactions.execute(status -> query.listTasks(
                principal, workspace, "CORE", null, 1, correlation));
        assertEquals(1, firstPage.items().size());
        assertTrue(firstPage.nextCursor().startsWith("aden-p1."));
        var secondPage = transactions.execute(status -> query.listTasks(
                principal, workspace, "CORE", firstPage.nextCursor(), 1, correlation));
        assertEquals(1, secondPage.items().size());
        assertTrue(!firstPage.items().get(0).taskId().equals(secondPage.items().get(0).taskId()));

        var queued = transactions.execute(status -> query.getTask(
                principal, workspace, first.taskId().value(), correlation));
        assertEquals("QUEUED", queued.state());
        assertEquals(List.of(com.ruoyi.aden.contract.OperatorTaskCommand.REQUEST_CANCEL),
                queued.allowedCommands());
        assertEquals(1, queued.steps().size());
        assertEquals("READY", queued.steps().get(0).state());

        var bootstrap = transactions.execute(status -> query.bootstrap(
                principal, workspace, null, null, 50, correlation));
        assertEquals(4, bootstrap.capabilities().size());
        assertEquals(1, bootstrap.runners().size());
        assertEquals(Long.toString(workspaceSequence(workspaceId)), bootstrap.streamWatermark());
        assertTrue(bootstrap.streamCursor().startsWith("aden-c1."));

        var events = transactions.execute(status -> query.events(
                principal, workspace, null, 100, correlation));
        assertEquals(workspaceSequence(workspaceId), events.items().size());
        assertTrue(events.items().stream().allMatch(event -> Set.of(
                "aden.task.created.v1", "aden.task.state-changed.v1").contains(event.eventType())));
        assertTrue(events.items().stream().allMatch(event -> event.data().keySet().stream().allMatch(Set.of(
                "from", "to", "reasonCode", "progressPercent", "runnerId",
                "capabilityCode", "externalActionsEnabled")::contains)));

        var audit = transactions.execute(status -> query.listAudit(
                principal, workspace, null, 100, correlation));
        assertEquals(3, audit.items().size());
        assertTrue(audit.items().stream().allMatch(item -> item.action().startsWith("aden.")));
        assertTrue(audit.items().stream().allMatch(item -> Set.of("SUCCEEDED", "DENIED", "FAILED")
                .contains(item.outcome())));
    }

    private static AdenRunnerReceiptService.ReceiptCommand receipt(
            AdenRunnerSessionPrincipal principal, String key, String deliveryId, long fence,
            long sequence, AdenReceiptType type, String correlationId) {
        return new AdenRunnerReceiptService.ReceiptCommand(principal, new AdenIdempotencyKey(key),
                new com.ruoyi.aden.domain.runner.AdenDeliveryId(deliveryId), fence, sequence,
                type, java.util.Map.of("fixture", type.name().toLowerCase()), correlationId);
    }

    private static AdenTaskTransactionService taskTransactionService(
            AdenTaskLedgerRepository ledger, long idStart) {
        AtomicLong ids = new AtomicLong(idStart);
        AdenIdGenerator generator = () -> String.format(
                "00000000-0000-4000-8000-%012d", ids.incrementAndGet());
        return new AdenTaskTransactionService(
                ledger, taskCasRepository,
                new AdenWorkspaceAccessGuard(repository, (principal, requested, correlation) -> { }),
                new AdenRequestFingerprint(objectMapper), new AdenSyntheticTaskValidator(), generator,
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC), 30, 3600);
    }

    private static AdenRunnerClaimService claimService(
            AdenRunnerDeliveryRepository repository, long idStart) {
        AtomicLong ids = new AtomicLong(idStart);
        return new AdenRunnerClaimService(
                repository, new AdenRequestFingerprint(objectMapper), objectMapper,
                () -> String.format("00000000-0000-4000-8000-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 60, 8, 30, 3600);
    }

    private static AdenTaskLedgerRepository failingAuditLedger(AdenTaskLedgerRepository delegate) {
        return (AdenTaskLedgerRepository) Proxy.newProxyInstance(
                AdenTaskLedgerRepository.class.getClassLoader(),
                new Class<?>[]{AdenTaskLedgerRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insertAudit")) {
                        throw new IllegalStateException("synthetic task audit failure");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static int count(String table, String workspaceId) {
        if (!Set.of("aden_task", "aden_task_step", "aden_runner_delivery", "aden_event", "aden_outbox",
                "aden_inbox", "aden_audit_event").contains(table)) {
            throw new IllegalArgumentException("拒绝查询未知测试表 " + table);
        }
        return jdbc.queryForObject(
                "select count(*) from " + table + " where workspace_id = ?", Integer.class, workspaceId);
    }

    private static long workspaceSequence(String workspaceId) {
        return jdbc.queryForObject(
                "select last_event_seq from aden_workspace where workspace_id = ?", Long.class, workspaceId);
    }

    private static void insertMember(String workspaceId, long userId, String role) {
        jdbc.update("insert into aden_workspace_member (workspace_id, ruoyi_user_id, workspace_role, "
                        + "member_status, version, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', 0, ?, ?)",
                workspaceId, userId, role,
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW),
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW));
    }

    private static void insertWorkspace(String workspaceId, long userId) {
        jdbc.update("insert into aden_workspace (workspace_id, workspace_name, workspace_status, "
                        + "last_event_seq, version, created_by_ruoyi_user_id, created_at, updated_at) "
                        + "values (?, ?, 'ACTIVE', 0, 0, ?, ?, ?)",
                workspaceId, "CAS " + workspaceId.substring(0, 4), userId,
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW),
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW));
    }

    private static void insertTask(String workspaceId, String taskId, String state,
                                   long version, long userId) {
        jdbc.update("insert into aden_task (workspace_id, task_id, task_type, required_capability, "
                        + "task_state, correlation_id, title, input_json, latest_step_no, version, "
                        + "created_by_ruoyi_user_id, created_at, updated_at) "
                        + "values (?, ?, 'SYNTHETIC_CORE', 'CORE', ?, ?, 'CAS task', json_object(), 0, ?, ?, ?, ?)",
                workspaceId, taskId, state, "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                version, userId,
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW),
                com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW));
    }

    private static void insertActiveRunnerSession(String workspaceId, String runnerId,
                                                  String credentialId, String sessionId,
                                                  long epoch, int capacity, long userId) {
        var now = com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW);
        var expires = com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec.toDatabase(NOW.plusSeconds(900));
        jdbc.update("insert into aden_runner(workspace_id,runner_id,runner_name,runner_presence,"
                        + "capabilities_json,current_session_epoch,current_credential_epoch,version,"
                        + "created_by_ruoyi_user_id,created_at,updated_at) "
                        + "values(?,?,?,'ONLINE',json_array('CORE'),?,1,0,?,?,?)",
                workspaceId, runnerId, "Runner " + runnerId.substring(0, 4), epoch, userId, now, now);
        jdbc.update("insert into aden_runner_credential(workspace_id,credential_id,runner_id,credential_epoch,"
                        + "credential_keyed_digest,pepper_key_id,credential_status,version,issued_at,expires_at,"
                        + "created_at,updated_at) values(?,?,?,1,?,'test','ACTIVE',0,?,?,?,?)",
                workspaceId, credentialId, runnerId, credentialId.replace("-", "").repeat(2),
                now, expires, now, now);
        jdbc.update("insert into aden_runner_session(workspace_id,session_id,runner_id,credential_id,"
                        + "session_status,session_epoch,session_keyed_digest,pepper_key_id,capacity,in_flight,"
                        + "heartbeat_sequence,metadata_json,version,heartbeat_at,expires_at,created_at,updated_at) "
                        + "values(?,?,?,?,'ACTIVE',?,?,'test',?,0,0,json_object(),0,?,?,?,?)",
                workspaceId, sessionId, runnerId, credentialId, epoch,
                sessionId.replace("-", "").repeat(2), capacity,
                now, expires, now, now);
    }

    private static AdenTask persistedTask(String workspaceId, String taskId, AdenTaskState state,
                                          long version, long userId) {
        return new AdenTask(new AdenWorkspaceId(workspaceId), new AdenTaskId(taskId),
                AdenTaskType.SYNTHETIC_CORE, AdenCapabilityCode.CORE, "CAS task", state,
                new AdenTaskVersion(version),
                new AdenCorrelationId("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
                userId, NOW, NOW);
    }

    private static AdenWorkspaceService service(AdenWorkspaceRepository target, String... ids) {
        List<String> sequence = new ArrayList<>(List.of(ids));
        AdenIdGenerator generator = () -> sequence.remove(0);
        return new AdenWorkspaceService(target, generator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private record FailingAuditRepository(AdenWorkspaceRepository delegate) implements AdenWorkspaceRepository {
        @Override
        public List<AdenWorkspaceMembership> findActiveByUserId(long userId, int limit) {
            return delegate.findActiveByUserId(userId, limit);
        }

        @Override
        public Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId workspaceId, long userId) {
            return delegate.findActiveMembership(workspaceId, userId);
        }

        @Override public void insertWorkspace(com.ruoyi.aden.domain.workspace.AdenWorkspace workspace) {
            delegate.insertWorkspace(workspace);
        }
        @Override public void insertInitialOwner(AdenWorkspaceMembership membership) {
            delegate.insertInitialOwner(membership);
        }
        @Override public void insertAudit(AdenWorkspaceAudit audit) {
            throw new IllegalStateException("synthetic audit failure");
        }
        @Override public void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit) {
            delegate.insertWorkspaceForbiddenAudit(audit);
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static String requiredEnvironment(String name) {
        String value = environment(name);
        if (value == null) throw new IllegalStateException("缺少 " + name);
        return value;
    }

    private static String withDatabase(String jdbcUrl, String targetDatabase) {
        int queryStart = jdbcUrl.indexOf('?');
        int pathEnd = queryStart < 0 ? jdbcUrl.length() : queryStart;
        int pathStart = jdbcUrl.indexOf('/', "jdbc:mysql://".length());
        String suffix = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
        return jdbcUrl.substring(0, pathStart + 1) + targetDatabase + suffix;
    }

    private static String identifier(String value) {
        if (!value.matches("^aden_test_[a-f0-9]{32}$")) {
            throw new IllegalArgumentException("拒绝操作非 Aden 随机测试库 " + value);
        }
        return '`' + value + '`';
    }

    private static Path workspacePath(String... parts) {
        Path current = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && current != null; up++, current = current.getParent()) {
            Path candidate = current;
            for (String part : parts) candidate = candidate.resolve(part);
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("无法定位 workspace 文件 " + String.join("/", parts));
    }
}
