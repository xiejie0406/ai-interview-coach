package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.Map;

/** 匿名注册专用 global/pre-tenant 幂等 owner；不能伪装成任何业务 Tenant。 */
public interface RegistrationIdempotencyPort {

    IdempotencyGuard.Decision begin(BeginCommand command);

    void succeed(CompleteCommand command);

    record BeginCommand(String operation, String requestHash, OperationContext context) {
        public BeginCommand {
            operation = DomainPreconditions.requireText(operation, "registrationOperation");
            requestHash = DomainPreconditions.requireText(requestHash, "registrationRequestHash");
            DomainPreconditions.requireNonNull(context, "operationContext");
            DomainPreconditions.require(context.principal().isEmpty() && context.serviceActor().isEmpty(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "pre-tenant idempotency requires an anonymous context");
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
            operation = DomainPreconditions.requireText(operation, "registrationOperation");
            requestHash = DomainPreconditions.requireText(requestHash, "registrationRequestHash");
            resourceReferences = Map.copyOf(resourceReferences == null ? Map.of() : resourceReferences);
            DomainPreconditions.require(responseStatus >= 200 && responseStatus < 400,
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "registration success status is invalid");
            DomainPreconditions.requireNonNull(context, "operationContext");
        }
    }
}
