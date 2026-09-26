package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.interview.internal.DefaultInterviewAgentCandidate;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.infrastructure.platform.UuidIdGeneratorAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultInterviewAgentCandidateTest {
    @Test
    void missingModelAliasContinuesWithNextPlannedQuestionWithoutCallingProvider() {
        var candidate = new DefaultInterviewAgentCandidate((request, context) -> {
            throw new AssertionError("未配置模型时不应调用外部 Provider");
        }, new UuidIdGeneratorAdapter(UUID::randomUUID), "");

        var result = candidate.propose(new InterviewAgentCandidate.Query(
                TenantId.of("test"), "question", "answer", 1, null));

        assertEquals(InterviewAgentCandidate.Result.Action.NEXT, result.action());
        assertEquals("MODEL_NOT_CONFIGURED", result.failureCode().orElseThrow());
    }
}
