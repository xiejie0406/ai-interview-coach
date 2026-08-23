package com.aiinterviewcoach.adapters.inbound.rest.common;

import com.aiinterviewcoach.application.identity.ResolvePrincipal;
import com.aiinterviewcoach.application.identity.ResolvedPrincipal;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.application.shared.QueryContext;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.IdempotencyKey;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP 边界唯一可信上下文工厂。tenant/user/role 只来自服务端 Session，绝不从 URL/body/header 接受覆盖。
 */
public final class RequestContextFactory {

    public static final String SESSION_COOKIE = "AIC_SESSION";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ResolvePrincipal principals;
    private final ClockPort clock;

    public RequestContextFactory(ResolvePrincipal principals, ClockPort clock) {
        this.principals = java.util.Objects.requireNonNull(principals);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public ResolvedPrincipal resolvedPrincipal(HttpServletRequest request) {
        return principals.handle(new ResolvePrincipal.Query(requireSessionToken(request), correlationId(request)));
    }

    public QueryContext query(HttpServletRequest request) {
        ResolvedPrincipal resolved = resolvedPrincipal(request);
        return new QueryContext(resolved.principalRef(), correlationId(request), clock.now());
    }

    public OperationContext operation(HttpServletRequest request) {
        ResolvedPrincipal resolved = resolvedPrincipal(request);
        return operation(request, Optional.of(resolved), principalScopeHash(resolved), true);
    }

    public OperationContext operationWithoutRequiredIdempotency(HttpServletRequest request) {
        ResolvedPrincipal resolved = resolvedPrincipal(request);
        return operation(request, Optional.of(resolved), principalScopeHash(resolved), false);
    }

    public OperationContext anonymousOperation(
            HttpServletRequest request,
            String anonymousScopeHash,
            boolean requireIdempotency
    ) {
        if (anonymousScopeHash == null || anonymousScopeHash.isBlank()) {
            throw invalid("anonymous principal scope is required");
        }
        return operation(request, Optional.empty(), anonymousScopeHash, requireIdempotency);
    }

    private OperationContext operation(
            HttpServletRequest request,
            Optional<ResolvedPrincipal> resolved,
            String scopeHash,
            boolean requireIdempotency
    ) {
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            if (requireIdempotency) {
                throw invalid("Idempotency-Key is required");
            }
            key = "request-" + correlationId(request).value();
        }
        if (key.length() < 16 || key.length() > 128) {
            throw invalid("Idempotency-Key length is invalid");
        }
        Instant now = clock.now();
        return new OperationContext(resolved.map(ResolvedPrincipal::principalRef), Optional.empty(), scopeHash,
                correlationId(request), new IdempotencyKey(key), now);
    }

    private static String principalScopeHash(ResolvedPrincipal principal) {
        return ServerSideDigest.sha256("http-principal", principal.principalRef().tenantId().value(),
                principal.principalRef().userId().value(), principal.sessionId().value());
    }

    private static String requireSessionToken(HttpServletRequest request) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElseGet(() -> new Cookie[0]))
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.AUTH_REQUIRED,
                        "session cookie is missing", false, Map.of()));
    }

    private static CorrelationId correlationId(HttpServletRequest request) {
        return new CorrelationId(CorrelationIdFilter.correlationId(request));
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException(reason);
    }
}
