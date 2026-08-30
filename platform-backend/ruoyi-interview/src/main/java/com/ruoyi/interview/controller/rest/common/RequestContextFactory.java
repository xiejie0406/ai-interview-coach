package com.ruoyi.interview.controller.rest.common;

import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.security.BusinessTenantResolver;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.configuration.RuoYiPrincipalFacade;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.IdempotencyKey;
import com.ruoyi.interview.domain.platform.PrincipalRef;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Optional;

/** RuoYi SecurityContext 到 AI application 上下文的唯一 HTTP 适配边界。 */
public final class RequestContextFactory {
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final RuoYiPrincipalFacade principals;
    private final BusinessTenantResolver tenants;
    private final ClockPort clock;

    public RequestContextFactory(RuoYiPrincipalFacade principals, BusinessTenantResolver tenants, ClockPort clock) {
        this.principals = java.util.Objects.requireNonNull(principals, "principals");
        this.tenants = java.util.Objects.requireNonNull(tenants, "tenants");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public PrincipalRef requiredPrincipal() {
        return principals.requiredDomainPrincipal(tenants);
    }

    public QueryContext query(HttpServletRequest request) {
        return new QueryContext(requiredPrincipal(), correlationId(request), clock.now());
    }

    public OperationContext operation(HttpServletRequest request) {
        return operation(request, true);
    }

    public OperationContext operationWithoutRequiredIdempotency(HttpServletRequest request) {
        return operation(request, false);
    }

    public OperationContext anonymousOperation(HttpServletRequest request, String anonymousScopeHash,
                                               boolean requireIdempotency) {
        if (anonymousScopeHash == null || anonymousScopeHash.isBlank()) {
            throw new IllegalArgumentException("anonymous principal scope is required");
        }
        return operationContext(request, Optional.empty(), anonymousScopeHash, requireIdempotency);
    }

    private OperationContext operation(HttpServletRequest request, boolean requireIdempotency) {
        PrincipalRef principal = requiredPrincipal();
        String scopeHash = ServerSideDigest.sha256("ruoyi-principal", principal.tenantId().value(),
                principal.userId().value());
        return operationContext(request, Optional.of(principal), scopeHash, requireIdempotency);
    }

    private OperationContext operationContext(HttpServletRequest request, Optional<PrincipalRef> principal,
                                              String scopeHash, boolean requireIdempotency) {
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            if (requireIdempotency) {
                throw new IllegalArgumentException("Idempotency-Key is required");
            }
            key = "request-" + correlationId(request).value();
        }
        if (key.length() < 16 || key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key length is invalid");
        }
        Instant now = clock.now();
        return new OperationContext(principal, Optional.empty(), scopeHash, correlationId(request),
                new IdempotencyKey(key), now);
    }

    private static CorrelationId correlationId(HttpServletRequest request) {
        return new CorrelationId(CorrelationIdFilter.correlationId(request));
    }
}
