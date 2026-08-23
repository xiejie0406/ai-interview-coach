package com.aiinterviewcoach.application.voice;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.application.shared.OperationAccepted;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.Optional;

@FunctionalInterface
public interface ConfirmTranscript {

    Result handle(Command command);

    record Command(ResourceId transcriptId, ResourceId transcriptVersionId,
                   Optional<String> correctedText, boolean lowConfidenceAcknowledged,
                   AggregateVersion expectedVersion, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
            DomainPreconditions.requireNonNull(transcriptVersionId, "transcriptVersionId");
            correctedText = correctedText == null ? Optional.empty() : correctedText;
            correctedText = correctedText.map(value -> {
                String checked = DomainPreconditions.requireText(value, "correctedText");
                DomainPreconditions.require(checked.length() <= 30_000,
                        com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                        "corrected text exceeds maximum length");
                return checked;
            });
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[transcriptId=" + transcriptId + ", transcriptVersionId="
                    + transcriptVersionId + ", correctedText=<redacted>, lowConfidenceAcknowledged="
                    + lowConfidenceAcknowledged + ", expectedVersion=" + expectedVersion
                    + ", context=" + context + "]";
        }
    }

    /** 与 OpenAPI ConfirmedTranscriptView 一一对应。 */
    record Result(
            ResourceId transcriptId,
            ResourceId confirmedTranscriptVersionId,
            ResourceId answerVersionId,
            OperationAccepted nextStepOperation,
            AggregateVersion version
    ) {
        public Result {
            DomainPreconditions.requireNonNull(transcriptId, "transcriptId");
            DomainPreconditions.requireNonNull(confirmedTranscriptVersionId,
                    "confirmedTranscriptVersionId");
            DomainPreconditions.requireNonNull(answerVersionId, "answerVersionId");
            DomainPreconditions.requireNonNull(nextStepOperation, "nextStepOperation");
            DomainPreconditions.requireNonNull(version, "transcriptVersion");
        }
    }
}
