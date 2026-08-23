package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Optional;

/** Durable SSE cursor projection；不得用进程内计数替代。 */
public interface EvaluationStreamCursorPort {
    Optional<String> current(TenantId tenantId, ResourceId evaluationId);
}
