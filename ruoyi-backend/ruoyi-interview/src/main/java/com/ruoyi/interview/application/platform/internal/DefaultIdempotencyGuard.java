package com.ruoyi.interview.application.platform.internal;

import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.IdempotencyPort;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.IdempotencyRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** 幂等记录的服务端 claim/complete 实现；同 key 不同摘要严格拒绝。 */
public final class DefaultIdempotencyGuard implements IdempotencyGuard {

    private final IdempotencyPort repository;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;
    private final Duration ttl;

    public DefaultIdempotencyGuard(IdempotencyPort repository, IdGeneratorPort idGenerator,
                                   ClockPort clock, Duration ttl) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.idGenerator = java.util.Objects.requireNonNull(idGenerator);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.ttl = requirePositive(ttl);
    }

    @Override
    public Decision begin(BeginCommand command) {
        var context = command.context();
        var tenantId = tenantScope(context);
        Instant now = clock.now();
        var existing = repository.find(tenantId, context.principalScopeHash(), command.operation(),
                context.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.orElseThrow();
            record.assertSameRequest(command.requestHash());
            return requireExecutableDecision(decision(record, now));
        }
        IdempotencyRecord candidate = IdempotencyRecord.start(idGenerator.nextResourceId(), tenantId,
                context.principalScopeHash(), command.operation(), context.idempotencyKey(), command.requestHash(),
                now.plus(ttl), now);
        if (repository.claim(candidate)) {
            repository.save(candidate);
            return new Decision(DecisionType.NEW, Map.of(), java.util.Optional.empty(), java.util.Optional.empty());
        }
        IdempotencyRecord raced = repository.find(tenantId, context.principalScopeHash(), command.operation(),
                context.idempotencyKey()).orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                "idempotency claim lost without a persisted record", true, Map.of()));
        raced.assertSameRequest(command.requestHash());
        return requireExecutableDecision(decision(raced, now));
    }

    @Override
    public void succeed(CompleteCommand command) {
        IdempotencyRecord record = load(command.context(), command.operation(), command.requestHash());
        var expected = record.version();
        record.succeed(command.resourceReferences(), command.responseStatus(), expected);
        repository.save(record);
    }

    @Override
    public void failReplayable(FailCommand command) {
        IdempotencyRecord record = load(command.context(), command.operation(), command.requestHash());
        var expected = record.version();
        record.failReplayable(command.errorCode(), command.responseStatus(), command.resourceReferences(), expected);
        repository.save(record);
    }

    private IdempotencyRecord load(com.ruoyi.interview.application.shared.OperationContext context,
                                   String operation, String requestHash) {
        IdempotencyRecord record = repository.find(tenantScope(context), context.principalScopeHash(),
                operation, context.idempotencyKey()).orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                "idempotency record is missing while completing operation", false, Map.of()));
        record.assertSameRequest(requestHash);
        return record;
    }

    /**
     * 匿名注册需要独立的 pre-tenant 幂等 owner；当前端口尚未表达它时安全失败，不能伪造 public tenant。
     */
    private static com.ruoyi.interview.domain.platform.TenantId tenantScope(
            com.ruoyi.interview.application.shared.OperationContext context) {
        return context.principal().map(value -> value.tenantId())
                .or(() -> context.serviceActor().map(value -> value.tenantId()))
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                        "pre-tenant idempotency storage is not configured",
                        false,
                        Map.of("reasonCode", "PRE_TENANT_IDEMPOTENCY_UNAVAILABLE")));
    }

    private static Decision requireExecutableDecision(Decision decision) {
        if (decision.type() == DecisionType.IN_PROGRESS) {
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    "operation with this idempotency key is still processing", true, Map.of());
        }
        if (decision.type() == DecisionType.REPLAY_FAILURE) {
            Map<String, String> metadata = new java.util.LinkedHashMap<>();
            decision.errorCode().ifPresent(value -> metadata.put("recordedErrorCode", value));
            decision.responseStatus().ifPresent(value -> metadata.put("recordedResponseStatus", value.toString()));
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                    "operation replays a previously recorded failure", false, metadata);
        }
        return decision;
    }

    private static Decision decision(IdempotencyRecord record, Instant now) {
        if (record.state() == com.ruoyi.interview.domain.platform.IdempotencyState.PROCESSING) {
            if (!now.isBefore(record.expiresAt())) {
                throw new DomainException(DomainErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "idempotency record has expired while still processing");
            }
            return new Decision(DecisionType.IN_PROGRESS, Map.of(), java.util.Optional.empty(),
                    java.util.Optional.empty());
        }
        if (record.state() == com.ruoyi.interview.domain.platform.IdempotencyState.SUCCEEDED) {
            return new Decision(DecisionType.REPLAY_SUCCESS, record.resourceReferences(), record.responseStatus(),
                    java.util.Optional.empty());
        }
        if (record.state() == com.ruoyi.interview.domain.platform.IdempotencyState.FAILED_REPLAYABLE) {
            return new Decision(DecisionType.REPLAY_FAILURE, record.resourceReferences(), record.responseStatus(),
                    record.errorCode());
        }
        throw new DomainException(DomainErrorCode.IDEMPOTENCY_CONFLICT,
                "idempotency key has expired and cannot be reused");
    }

    private static Duration requirePositive(Duration value) {
        java.util.Objects.requireNonNull(value, "idempotencyTtl");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("idempotencyTtl must be positive");
        }
        return value;
    }
}
