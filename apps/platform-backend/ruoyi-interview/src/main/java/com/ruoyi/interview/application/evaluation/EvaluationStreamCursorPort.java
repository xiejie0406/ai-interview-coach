package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

/** Durable SSE cursor projection；不得用进程内计数替代。 */
public interface EvaluationStreamCursorPort {
    Optional<String> current(TenantId tenantId, ResourceId evaluationId);
}
