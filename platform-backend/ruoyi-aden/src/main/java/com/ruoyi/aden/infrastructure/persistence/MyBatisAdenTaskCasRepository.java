package com.ruoyi.aden.infrastructure.persistence;

import com.ruoyi.aden.application.error.AdenVersionConflictException;
import com.ruoyi.aden.application.task.AdenTaskCasRepository;
import com.ruoyi.aden.domain.task.AdenTask;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenTaskMapper;
import com.ruoyi.aden.infrastructure.time.AdenUtcDateTimeCodec;

import java.util.Objects;

public final class MyBatisAdenTaskCasRepository implements AdenTaskCasRepository {
    private final AdenTaskMapper mapper;

    public MyBatisAdenTaskCasRepository(AdenTaskMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void updateState(AdenTask previous, AdenTask next) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        if (!previous.workspaceId().equals(next.workspaceId()) || !previous.id().equals(next.id())) {
            throw new IllegalArgumentException("CAS 前后 Task 标识必须一致");
        }
        if (next.version().value() != previous.version().value() + 1) {
            throw new IllegalArgumentException("CAS 成功只能增加一个 Task version");
        }
        int rows = mapper.updateStateCas(
                previous.workspaceId().value(), previous.id().value(),
                previous.version().value(), previous.state().name(),
                next.version().value(), next.state().name(),
                AdenUtcDateTimeCodec.toDatabase(next.updatedAt()));
        if (rows != 1) {
            throw new AdenVersionConflictException(
                    Long.toString(previous.version().value()), previous.state().name());
        }
    }
}
