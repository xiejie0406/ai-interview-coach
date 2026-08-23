package com.aiinterviewcoach.adapters.outbound.persistence.identity;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.PersistenceJsonCodec;
import com.aiinterviewcoach.application.identity.port.RegistrationIdempotencyPort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** 无 tenant FK 的匿名注册幂等 owner；scope 只保存 keyed hash，不保存 email 或 proof。 */
@Transactional(readOnly = true)
public class JdbcRegistrationIdempotencyRepository implements RegistrationIdempotencyPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final PersistenceJsonCodec json;
    private final Duration ttl;

    public JdbcRegistrationIdempotencyRepository(
            NamedParameterJdbcTemplate jdbc,
            PersistenceJsonCodec json,
            Duration ttl
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.json = java.util.Objects.requireNonNull(json);
        this.ttl = positive(ttl);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyGuard.Decision begin(BeginCommand command) {
        MapSqlParameterSource parameters = parameters(command.operation(), command.requestHash(), command.context())
                .addValue("expiresAt", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport
                        .writeInstant(command.context().requestedAt().plus(ttl)));
        int inserted = jdbc.update("""
                insert into platform.pre_tenant_idempotency_record (
                    principal_scope_hash, operation, idempotency_key, request_hash,
                    expires_at, state, resource_references, response_status, error_code
                ) values (
                    :scopeHash, :operation, :idempotencyKey, :requestHash,
                    :expiresAt, 'PROCESSING', '{}'::jsonb, null, null
                ) on conflict do nothing
                """, parameters);
        if (inserted == 1) {
            return new IdempotencyGuard.Decision(IdempotencyGuard.DecisionType.NEW, Map.of(),
                    Optional.empty(), Optional.empty());
        }
        Stored stored = load(parameters);
        if (!stored.requestHash().equals(command.requestHash())) {
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                    "idempotency key belongs to another request", false,
                    Map.of("reasonCode", "IDEMPOTENCY_KEY_REUSED"));
        }
        return switch (stored.state()) {
            case "PROCESSING" -> {
                if (!command.context().requestedAt().isBefore(stored.expiresAt())) {
                    throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                            "expired registration idempotency record cannot be reused", false,
                            Map.of("reasonCode", "IDEMPOTENCY_RECORD_EXPIRED"));
                }
                yield new IdempotencyGuard.Decision(IdempotencyGuard.DecisionType.IN_PROGRESS, Map.of(),
                        Optional.empty(), Optional.empty());
            }
            case "SUCCEEDED" -> new IdempotencyGuard.Decision(IdempotencyGuard.DecisionType.REPLAY_SUCCESS,
                    stored.resourceReferences(), Optional.ofNullable(stored.responseStatus()), Optional.empty());
            case "FAILED_REPLAYABLE" -> new IdempotencyGuard.Decision(
                    IdempotencyGuard.DecisionType.REPLAY_FAILURE, stored.resourceReferences(),
                    Optional.ofNullable(stored.responseStatus()), Optional.ofNullable(stored.errorCode()));
            case "EXPIRED" -> throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_REPLAY_FAILURE,
                    "expired registration idempotency record cannot be reused", false,
                    Map.of("reasonCode", "IDEMPOTENCY_RECORD_EXPIRED"));
            default -> throw new DataIntegrityViolationException("unknown pre-tenant idempotency state");
        };
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void succeed(CompleteCommand command) {
        MapSqlParameterSource parameters = parameters(command.operation(), command.requestHash(), command.context())
                .addValue("references", json.write(command.resourceReferences()))
                .addValue("responseStatus", command.responseStatus());
        int updated = jdbc.update("""
                update platform.pre_tenant_idempotency_record
                   set state = 'SUCCEEDED', resource_references = cast(:references as jsonb),
                       response_status = :responseStatus, error_code = null
                 where principal_scope_hash = :scopeHash and operation = :operation
                   and idempotency_key = :idempotencyKey and request_hash = :requestHash
                   and state = 'PROCESSING'
                """, parameters);
        if (updated != 1) {
            throw new DataIntegrityViolationException("registration idempotency completion conflict");
        }
    }

    private Stored load(MapSqlParameterSource parameters) {
        var rows = jdbc.query("""
                select request_hash, expires_at, state, resource_references, response_status, error_code
                  from platform.pre_tenant_idempotency_record
                 where principal_scope_hash = :scopeHash and operation = :operation
                   and idempotency_key = :idempotencyKey
                 for update
                """, parameters, (row, rowNum) -> new Stored(row.getString("request_hash"),
                row.getObject("expires_at", java.time.OffsetDateTime.class).toInstant(), row.getString("state"),
                json.readStringMap(row.getString("resource_references")),
                (Integer) row.getObject("response_status"), row.getString("error_code")));
        if (rows.size() != 1) {
            throw new DataIntegrityViolationException("registration idempotency claim was lost");
        }
        return rows.getFirst();
    }

    private static MapSqlParameterSource parameters(
            String operation,
            String requestHash,
            com.aiinterviewcoach.application.shared.OperationContext context
    ) {
        return new MapSqlParameterSource().addValue("scopeHash", context.principalScopeHash())
                .addValue("operation", operation).addValue("idempotencyKey", context.idempotencyKey().value())
                .addValue("requestHash", requestHash);
    }

    private static Duration positive(Duration value) {
        java.util.Objects.requireNonNull(value, "registrationIdempotencyTtl");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("registration idempotency TTL must be positive");
        }
        return value;
    }

    private record Stored(String requestHash, Instant expiresAt, String state,
                          Map<String, String> resourceReferences, Integer responseStatus, String errorCode) { }
}
