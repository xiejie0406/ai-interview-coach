package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.Map;
import java.util.Optional;

/**
 * 每个高风险命令在业务事务开始时调用。默认实现只把 NEW/REPLAY_SUCCESS 返回给业务用例；
 * IN_PROGRESS 与 REPLAY_FAILURE 以稳定应用异常终止，避免调用方遗漏分支后重复副作用。
 */
public interface IdempotencyGuard {

    Decision begin(BeginCommand command);

    void succeed(CompleteCommand command);

    void failReplayable(FailCommand command);

    enum DecisionType {
        NEW,
        IN_PROGRESS,
        REPLAY_SUCCESS,
        REPLAY_FAILURE
    }

    record BeginCommand(String operation, String requestHash, OperationContext context) {
        public BeginCommand {
            operation = DomainPreconditions.requireText(operation, "operation");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
        }

        @Override
        public String toString() {
            return "BeginCommand[operation=" + operation + ", requestHash=<redacted>"
                    + ", context=" + context + "]";
        }
    }

    record Decision(
            DecisionType type,
            Map<String, String> resourceReferences,
            Optional<Integer> responseStatus,
            Optional<String> errorCode
    ) {
        public Decision {
            DomainPreconditions.requireNonNull(type, "decisionType");
            resourceReferences = Map.copyOf(resourceReferences == null ? Map.of() : resourceReferences);
            responseStatus = responseStatus == null ? Optional.empty() : responseStatus;
            errorCode = errorCode == null ? Optional.empty() : errorCode;
        }

        @Override
        public String toString() {
            return "Decision[type=" + type + ", resourceReferenceCount=" + resourceReferences.size()
                    + ", responseStatus=" + responseStatus + ", errorCode=" + errorCode + "]";
        }
    }

    record CompleteCommand(
            String operation,
            String requestHash,
            Map<String, String> resourceReferences,
            int responseStatus,
            OperationContext context
    ) {
        public CompleteCommand {
            operation = DomainPreconditions.requireText(operation, "operation");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            resourceReferences = Map.copyOf(resourceReferences == null ? Map.of() : resourceReferences);
            DomainPreconditions.requireNonNull(context, "operationContext");
        }

        @Override
        public String toString() {
            return "CompleteCommand[operation=" + operation + ", requestHash=<redacted>"
                    + ", resourceReferenceCount=" + resourceReferences.size()
                    + ", responseStatus=" + responseStatus + ", context=" + context + "]";
        }
    }

    record FailCommand(
            String operation,
            String requestHash,
            String errorCode,
            int responseStatus,
            Map<String, String> resourceReferences,
            OperationContext context
    ) {
        public FailCommand {
            operation = DomainPreconditions.requireText(operation, "operation");
            requestHash = DomainPreconditions.requireText(requestHash, "requestHash");
            errorCode = DomainPreconditions.requireText(errorCode, "errorCode");
            resourceReferences = Map.copyOf(resourceReferences == null ? Map.of() : resourceReferences);
            DomainPreconditions.requireNonNull(context, "operationContext");
        }

        @Override
        public String toString() {
            return "FailCommand[operation=" + operation + ", requestHash=<redacted>"
                    + ", errorCode=" + errorCode + ", responseStatus=" + responseStatus
                    + ", resourceReferenceCount=" + resourceReferences.size()
                    + ", context=" + context + "]";
        }
    }
}
