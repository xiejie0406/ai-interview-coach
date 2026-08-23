package com.ruoyi.interview.application.shared;

import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.IdempotencyKey;
import com.ruoyi.interview.domain.platform.PrincipalRef;
import com.ruoyi.interview.domain.platform.ServiceActorRef;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;
import java.util.Optional;

/** 命令入口的可信上下文；principal 只能由认证适配器解析，不能从请求 body 构造。 */
public record OperationContext(
        Optional<PrincipalRef> principal,
        Optional<ServiceActorRef> serviceActor,
        String principalScopeHash,
        CorrelationId correlationId,
        IdempotencyKey idempotencyKey,
        Instant requestedAt
) {

    public OperationContext {
        principal = principal == null ? Optional.empty() : principal;
        serviceActor = serviceActor == null ? Optional.empty() : serviceActor;
        DomainPreconditions.require(!(principal.isPresent() && serviceActor.isPresent()),
                DomainErrorCode.INVALID_ARGUMENT, "operation cannot have both user and service actors");
        principalScopeHash = DomainPreconditions.requireText(principalScopeHash, "principalScopeHash");
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
        DomainPreconditions.requireNonNull(idempotencyKey, "idempotencyKey");
        DomainPreconditions.requireNonNull(requestedAt, "requestedAt");
    }

    public PrincipalRef requirePrincipal() {
        return principal.orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.AUTH_REQUIRED,
                "authenticated user principal is required",
                false,
                java.util.Map.of()));
    }

    public void requireAuthenticatedActor() {
        if (principal.isEmpty() && serviceActor.isEmpty()) {
            throw new ApplicationException(
                    ApplicationErrorCode.AUTH_REQUIRED,
                    "authenticated user or service actor is required",
                    false,
                    java.util.Map.of());
        }
    }

    public ServiceActorRef requireServiceActor() {
        return serviceActor.orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.FORBIDDEN,
                "authenticated service actor is required",
                false,
                java.util.Map.of()));
    }

    public TenantId requireTenantScope() {
        if (principal.isPresent()) {
            return principal.orElseThrow().tenantId();
        }
        if (serviceActor.isPresent()) {
            return serviceActor.orElseThrow().tenantId();
        }
        throw new ApplicationException(
                ApplicationErrorCode.AUTH_REQUIRED,
                "authenticated tenant scope is required",
                false,
                java.util.Map.of());
    }

    public EventContext eventContext() {
        return new EventContext(correlationId, requestedAt);
    }

    @Override
    public String toString() {
        return "OperationContext[principal=" + (principal.isPresent() ? "<present>" : "<absent>")
                + ", serviceActor=" + (serviceActor.isPresent() ? "<present>" : "<absent>")
                + ", principalScopeHash=<redacted>, correlationId=" + correlationId
                + ", idempotencyKey=<redacted>, requestedAt=" + requestedAt + "]";
    }
}
