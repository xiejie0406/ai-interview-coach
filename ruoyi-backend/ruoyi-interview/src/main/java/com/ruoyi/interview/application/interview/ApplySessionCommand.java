package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.interview.SessionCommandType;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.Optional;
import java.util.Set;

/** 入站 adapter 只能调用此用例，不能自行加载聚合或赋值 SessionState。 */
@FunctionalInterface
public interface ApplySessionCommand {

    InterviewSessionSnapshot handle(Command command);

    record Command(
            ResourceId sessionId,
            SessionCommandType commandType,
            Optional<String> reasonCode,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(commandType, "commandType");
            DomainPreconditions.require(Set.of(
                            SessionCommandType.PAUSE,
                            SessionCommandType.RESUME,
                            SessionCommandType.SKIP,
                            SessionCommandType.COMPLETE,
                            SessionCommandType.CANCEL,
                            SessionCommandType.RECOVER).contains(commandType),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "command must use its dedicated use case when one exists");
            reasonCode = reasonCode == null ? Optional.empty() : reasonCode;
            reasonCode = reasonCode.map(value -> DomainPreconditions.requireText(value, "reasonCode"));
            reasonCode.ifPresent(value -> DomainPreconditions.require(value.length() <= 96,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "reasonCode is too long"));
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[sessionId=" + sessionId + ", commandType=" + commandType
                    + ", reasonCode=" + (reasonCode.isPresent() ? "<present>" : "<absent>")
                    + ", expectedVersion=" + expectedVersion + ", context=" + context + "]";
        }
    }
}
