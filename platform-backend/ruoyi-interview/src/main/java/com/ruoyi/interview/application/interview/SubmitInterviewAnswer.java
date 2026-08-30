package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.OperationAccepted;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.interview.InterviewAnswerSource;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;

/**
 * REST 文本回答入口；Confirmed Transcript 必须由 ConfirmTranscript 的 consumer-owned bridge 原子提交。
 * AnswerVersion、Turn 稳定点、next Job/Outbox 与幂等结果仍在同一本地事务。
 */
@FunctionalInterface
public interface SubmitInterviewAnswer {

    Result handle(Command command);

    record Command(
            ResourceId sessionId,
            ResourceId turnId,
            int turnSequence,
            InterviewAnswerSource source,
            String answerText,
            Optional<ResourceId> confirmedTranscriptVersionId,
            AggregateVersion expectedSessionVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            DomainPreconditions.require(turnSequence > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "turn sequence must be positive");
            DomainPreconditions.requireNonNull(source, "answerSource");
            answerText = DomainPreconditions.requireText(answerText, "answerText");
            DomainPreconditions.require(answerText.length() <= 30_000,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "answer text exceeds maximum length");
            confirmedTranscriptVersionId = confirmedTranscriptVersionId == null
                    ? Optional.empty() : confirmedTranscriptVersionId;
            DomainPreconditions.require((source == InterviewAnswerSource.CONFIRMED_TRANSCRIPT)
                            == confirmedTranscriptVersionId.isPresent(),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "answer source and transcript reference are inconsistent");
            DomainPreconditions.requireNonNull(expectedSessionVersion, "expectedSessionVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[sessionId=" + sessionId + ", turnId=" + turnId + ", turnSequence="
                    + turnSequence + ", source=" + source + ", answerText=<redacted>"
                    + ", confirmedTranscriptVersionId=" + confirmedTranscriptVersionId
                    + ", expectedSessionVersion=" + expectedSessionVersion + ", context=" + context + "]";
        }
    }

    record Result(
            InterviewSessionSnapshot snapshot,
            ResourceId answerVersionId,
            OperationAccepted nextStepOperation
    ) {
        public Result {
            DomainPreconditions.requireNonNull(snapshot, "sessionSnapshot");
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            DomainPreconditions.requireNonNull(nextStepOperation, "nextStepOperation");
        }
    }
}
