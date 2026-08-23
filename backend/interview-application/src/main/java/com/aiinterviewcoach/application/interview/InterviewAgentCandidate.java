package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Optional;

/** DeepSeek 等模型只提供候选动作；Session 仍拥有状态和预算裁决。 */
@FunctionalInterface
public interface InterviewAgentCandidate {
    Result propose(Query query);

    record Query(TenantId tenantId, String questionText, String confirmedAnswer,
                 int remainingFollowUpBudget, OperationContext context) { }
    record Result(Action action, Optional<String> questionText, Optional<String> failureCode) {
        public enum Action { FOLLOW_UP, NEXT }
    }
}
