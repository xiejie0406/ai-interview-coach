package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.domain.evaluation.EvaluationPolicySnapshot;
import com.aiinterviewcoach.domain.evaluation.EvaluationSourceRef;
import com.aiinterviewcoach.domain.platform.TenantId;

/** 服务端解析当前可用策略并返回一次性固定快照；请求方不能提交 Provider/Prompt 配置。 */
public interface EvaluationPolicyPort {
    EvaluationPolicySnapshot resolve(TenantId tenantId, EvaluationSourceRef sourceRef);
}
