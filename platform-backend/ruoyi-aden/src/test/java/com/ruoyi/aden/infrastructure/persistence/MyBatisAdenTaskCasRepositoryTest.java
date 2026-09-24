package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.error.AdenVersionConflictException;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenCorrelationId;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.domain.task.AdenTaskActor;
import com.ruoyi.aden.domain.task.AdenTaskCommand;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskType;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MyBatisAdenTaskCasRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-13T02:20:00Z");

    @Test
    void delegatesAllFourCasPredicatesAndAllowsExactlyOneVersion() {
        RecordingMapper mapper = new RecordingMapper(1);
        MyBatisAdenTaskCasRepository repository = new MyBatisAdenTaskCasRepository(mapper.asMapper());
        AdenTask previous = task(AdenTaskState.QUEUED, 6);
        AdenTask next = previous.transition(
                AdenTaskActor.RUNNER, AdenTaskCommand.START, NOW.plusSeconds(1)).task();

        repository.updateState(previous, next);

        assertEquals(previous.workspaceId().value(), mapper.workspaceId);
        assertEquals(previous.id().value(), mapper.taskId);
        assertEquals(6, mapper.expectedVersion);
        assertEquals("QUEUED", mapper.expectedState);
        assertEquals(7, mapper.nextVersion);
        assertEquals("RUNNING", mapper.nextState);
    }

    @Test
    void zeroRowsIsADomainVersionConflict() {
        MyBatisAdenTaskCasRepository repository = new MyBatisAdenTaskCasRepository(
                new RecordingMapper(0).asMapper());
        AdenTask previous = task(AdenTaskState.QUEUED, 6);
        AdenTask next = previous.transition(
                AdenTaskActor.RUNNER, AdenTaskCommand.START, NOW.plusSeconds(1)).task();

        AdenVersionConflictException exception = assertThrows(
                AdenVersionConflictException.class, () -> repository.updateState(previous, next));

        assertEquals("ADEN_VERSION_CONFLICT", exception.errorCode());
        assertEquals("6", exception.details().get("expectedVersion"));
        assertEquals("QUEUED", exception.details().get("expectedState"));
    }

    @Test
    void identityOrVersionJumpCannotReachSql() {
        RecordingMapper mapper = new RecordingMapper(1);
        MyBatisAdenTaskCasRepository repository = new MyBatisAdenTaskCasRepository(mapper.asMapper());
        AdenTask previous = task(AdenTaskState.QUEUED, 6);
        AdenTask wrongVersion = new AdenTask(
                previous.workspaceId(), previous.id(), previous.type(), previous.capability(), previous.title(),
                AdenTaskState.RUNNING, new AdenTaskVersion(8), previous.correlationId(),
                previous.createdByRuoYiUserId(), previous.createdAt(), NOW.plusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> repository.updateState(previous, wrongVersion));
        assertEquals(0, mapper.calls);
    }

    private static AdenTask task(AdenTaskState state, long version) {
        return new AdenTask(
                new AdenWorkspaceId("11111111-1111-4111-8111-111111111111"),
                new AdenTaskId("22222222-2222-4222-8222-222222222222"),
                AdenTaskType.SYNTHETIC_CORE, AdenCapabilityCode.CORE, "CAS 任务", state,
                new AdenTaskVersion(version),
                new AdenCorrelationId("33333333-3333-4333-8333-333333333333"),
                42, NOW, NOW);
    }

    private static final class RecordingMapper {
        private final int rows;
        private int calls;
        private String workspaceId;
        private String taskId;
        private long expectedVersion;
        private String expectedState;
        private long nextVersion;
        private String nextState;

        private RecordingMapper(int rows) {
            this.rows = rows;
        }

        private AdenTaskMapper asMapper() {
            return (AdenTaskMapper) Proxy.newProxyInstance(
                    AdenTaskMapper.class.getClassLoader(), new Class<?>[]{AdenTaskMapper.class},
                    (proxy, method, arguments) -> {
                        if (!method.getName().equals("updateStateCas")) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        calls++;
                        workspaceId = (String) arguments[0];
                        taskId = (String) arguments[1];
                        expectedVersion = (long) arguments[2];
                        expectedState = (String) arguments[3];
                        nextVersion = (long) arguments[4];
                        nextState = (String) arguments[5];
                        return rows;
                    });
        }
    }
}
