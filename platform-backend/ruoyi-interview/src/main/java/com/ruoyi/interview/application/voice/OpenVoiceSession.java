package com.ruoyi.interview.application.voice;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

@FunctionalInterface
public interface OpenVoiceSession {

    Result handle(Command command);

    record Command(ResourceId interviewId, ResourceId turnId, String codec,
                   AggregateVersion expectedSessionVersion, OperationContext context) {
        public Command {
            DomainPreconditions.requireNonNull(interviewId, "interviewId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            codec = DomainPreconditions.requireText(codec, "voiceCodec");
            DomainPreconditions.requireNonNull(expectedSessionVersion, "expectedSessionVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(VoiceSessionTicketPort.Handle handle, ResourceId executionId,
                  ResourceId inputArtifactId, AggregateVersion executionVersion,
                  long socketGeneration, long initialServerSequence,
                  FlowControl flowControl) {
        public Result {
            DomainPreconditions.requireNonNull(handle, "voiceSessionHandle");
            DomainPreconditions.requireNonNull(executionId, "voiceExecutionId");
            DomainPreconditions.requireNonNull(inputArtifactId, "inputArtifactId");
            DomainPreconditions.requireNonNull(executionVersion, "voiceExecutionVersion");
            DomainPreconditions.require(socketGeneration > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "socket generation must be positive");
            DomainPreconditions.require(initialServerSequence >= 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "initial server sequence must not be negative");
            DomainPreconditions.requireNonNull(flowControl, "voiceFlowControl");
        }
    }

    record FlowControl(int protocolVersion, int maximumInFlightChunks,
                       long maximumChunkBytes, long maximumBufferedDurationMillis,
                       int maximumDurationSeconds, long maximumBytes) {
        public FlowControl {
            DomainPreconditions.require(protocolVersion == 1,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "unsupported voice protocol version");
            DomainPreconditions.require(maximumInFlightChunks > 0 && maximumChunkBytes > 0
                            && maximumBufferedDurationMillis > 0 && maximumDurationSeconds > 0
                            && maximumBytes > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "voice flow limits must be positive");
            DomainPreconditions.require(maximumChunkBytes <= maximumBytes
                            && maximumBufferedDurationMillis <= maximumDurationSeconds * 1000L,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "voice flow limits exceed session limits");
        }
    }
}
