package com.aiinterviewcoach.application.agent.interview;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/** 只在受控调用内存中传递的已确认回答快照；不得写入普通日志或 Prompt 审计元数据。 */
public record ConfirmedAnswerInput(
        ResourceId answerVersionId,
        String text,
        String contentHash
) {

    public ConfirmedAnswerInput {
        DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
        text = DomainPreconditions.requireText(text, "confirmedAnswerText");
        contentHash = DomainPreconditions.requireText(contentHash, "confirmedAnswerContentHash");
    }

    @Override
    public String toString() {
        return "ConfirmedAnswerInput[answerVersionId=" + answerVersionId
                + ", text=<redacted>, contentHash=<redacted>]";
    }
}
