package com.aiinterviewcoach.application.voice.port;

import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

/**
 * Voice consumer-owned、Interview provider 实现的本地事务桥。调用方必须已打开同一个本地事务；
 * provider 会重新解析活跃 principal、tenant、owner 与当前 Session/Turn，不接受客户端 Session ETag。
 */
@FunctionalInterface
public interface ConfirmedTranscriptAnswerPort {

    Result commit(Command command);

    record Command(
            ResourceId sessionId,
            ResourceId turnId,
            ResourceId confirmedTranscriptVersionId,
            String confirmedText,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            DomainPreconditions.requireNonNull(confirmedTranscriptVersionId,
                    "confirmedTranscriptVersionId");
            confirmedText = DomainPreconditions.requireText(confirmedText, "confirmedTranscriptText");
            DomainPreconditions.require(confirmedText.length() <= 30_000,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "confirmed transcript text exceeds maximum length");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[sessionId=" + sessionId + ", turnId=" + turnId
                    + ", confirmedTranscriptVersionId=" + confirmedTranscriptVersionId
                    + ", confirmedText=<redacted>, context=" + context + "]";
        }
    }

    record Result(
            ResourceId answerVersionId,
            OperationAccepted nextStepOperation,
            AggregateVersion sessionVersion
    ) {
        public Result {
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            DomainPreconditions.requireNonNull(nextStepOperation, "nextStepOperation");
            DomainPreconditions.requireNonNull(sessionVersion, "sessionVersion");
        }
    }
}
