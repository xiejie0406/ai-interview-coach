package com.ruoyi.interview.infrastructure.persistence.evaluation;

import com.ruoyi.interview.application.evaluation.EvaluationStreamCursorPort;
import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.domain.platform.DurableStreamType;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Evaluation snapshot 只读取 PostgreSQL durable stream head，不使用进程内 sequence。 */
@Repository
public class JdbcEvaluationStreamCursorRepository implements EvaluationStreamCursorPort {

    private final DurableStreamPort streams;

    public JdbcEvaluationStreamCursorRepository(DurableStreamPort streams) {
        this.streams = java.util.Objects.requireNonNull(streams);
    }

    @Override
    public Optional<String> current(TenantId tenantId, ResourceId evaluationId) {
        return streams.currentCursor(tenantId, DurableStreamType.EVALUATION, evaluationId);
    }
}

