package com.aiinterviewcoach.adapters.outbound.persistence.evaluation;

import com.aiinterviewcoach.application.evaluation.EvaluationStreamCursorPort;
import com.aiinterviewcoach.application.platform.port.DurableStreamPort;
import com.aiinterviewcoach.domain.platform.DurableStreamType;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
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
