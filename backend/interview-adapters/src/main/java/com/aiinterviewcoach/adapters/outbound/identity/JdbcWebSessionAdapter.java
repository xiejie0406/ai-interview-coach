package com.aiinterviewcoach.adapters.outbound.identity;

import com.aiinterviewcoach.application.identity.ResolvedPrincipal;
import com.aiinterviewcoach.application.identity.port.WebSessionPort;
import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.platform.PrincipalRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 服务端 opaque Cookie Session；数据库只保存 HMAC token/CSRF hash，不保存可用 bearer。 */
@Transactional
public class JdbcWebSessionAdapter implements WebSessionPort {

    private static final int TOKEN_BYTES = 32;

    private final NamedParameterJdbcTemplate jdbc;
    private final HmacSha256 tokens;
    private final SecureRandom random;
    private final Clock clock;
    private final Duration absoluteTtl;
    private final Duration idleTtl;

    public JdbcWebSessionAdapter(
            NamedParameterJdbcTemplate jdbc,
            byte[] tokenHmacKey,
            SecureRandom random,
            Clock clock,
            Duration absoluteTtl,
            Duration idleTtl
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.tokens = new HmacSha256(tokenHmacKey);
        this.random = java.util.Objects.requireNonNull(random);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.absoluteTtl = positive(absoluteTtl, "sessionAbsoluteTtl");
        this.idleTtl = positive(idleTtl, "sessionIdleTtl");
        if (idleTtl.compareTo(absoluteTtl) > 0) {
            throw new IllegalArgumentException("session idle TTL cannot exceed absolute TTL");
        }
    }

    @Override
    public IssuedSession issue(PrincipalRef principal, Instant issuedAt) {
        java.util.Objects.requireNonNull(principal, "principal");
        java.util.Objects.requireNonNull(issuedAt, "issuedAt");
        ResourceId sessionId = ResourceId.of(java.util.UUID.randomUUID());
        String sessionToken = randomToken();
        String csrfToken = randomToken();
        Instant expiresAt = issuedAt.plus(absoluteTtl);
        Instant idleExpiresAt = issuedAt.plus(idleTtl);
        int inserted = jdbc.update("""
                insert into identity.web_session (
                    tenant_id, session_id, user_id, token_hash, csrf_hash,
                    issued_at, expires_at, idle_expires_at, last_seen_at, revoked_at
                ) values (
                    :tenantId, :sessionId, :userId, :tokenHash, :csrfHash,
                    :issuedAt, :expiresAt, :idleExpiresAt, :issuedAt, null
                )
                """, new MapSqlParameterSource().addValue("tenantId", principal.tenantId().value())
                .addValue("sessionId", sessionId.value()).addValue("userId", principal.userId().value())
                .addValue("tokenHash", tokenHash(sessionToken)).addValue("csrfHash", csrfHash(csrfToken))
                .addValue("issuedAt", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(issuedAt))
                .addValue("expiresAt", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(expiresAt))
                .addValue("idleExpiresAt", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(idleExpiresAt)));
        if (inserted != 1) {
            throw new DataIntegrityViolationException("web session was not issued");
        }
        return new IssuedSession(sessionId, sessionToken, csrfToken, expiresAt, idleExpiresAt);
    }

    @Override
    public Optional<ResolvedPrincipal> resolve(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank() || sessionToken.length() > 512) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        List<SessionRow> rows = jdbc.query("""
                select s.tenant_id, s.session_id, s.user_id, s.expires_at, m.role
                  from identity.web_session s
                  join identity.membership m
                    on m.tenant_id = s.tenant_id and m.user_id = s.user_id and m.status = 'ACTIVE'
                 where s.token_hash = :tokenHash and s.revoked_at is null
                   and s.expires_at > :now and s.idle_expires_at > :now
                """, new MapSqlParameterSource().addValue("tokenHash", tokenHash(sessionToken))
                .addValue("now", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(now)), (row, rowNum) -> new SessionRow(
                TenantId.of(row.getString("tenant_id")), ResourceId.of(row.getString("session_id")),
                UserId.of(row.getString("user_id")), MembershipRole.valueOf(row.getString("role")),
                row.getObject("expires_at", java.time.OffsetDateTime.class).toInstant()));
        if (rows.size() != 1) {
            return Optional.empty();
        }
        SessionRow row = rows.getFirst();
        Instant nextIdle = min(row.expiresAt(), now.plus(idleTtl));
        int touched = jdbc.update("""
                update identity.web_session
                   set last_seen_at = :now, idle_expires_at = :idleExpiresAt
                 where tenant_id = :tenantId and session_id = :sessionId
                   and token_hash = :tokenHash and revoked_at is null
                   and expires_at > :now and idle_expires_at > :now
                """, new MapSqlParameterSource()
                .addValue("now", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(now))
                .addValue("idleExpiresAt", com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(nextIdle))
                .addValue("tenantId", row.tenantId().value()).addValue("sessionId", row.sessionId().value())
                .addValue("tokenHash", tokenHash(sessionToken)));
        if (touched != 1) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPrincipal(new PrincipalRef(row.tenantId(), row.userId()), row.sessionId(),
                Set.of(row.role()), row.expiresAt()));
    }

    @Override
    public IssuedSession rotate(String currentSessionToken, Instant rotatedAt) {
        Optional<ResolvedPrincipal> current = resolve(currentSessionToken);
        ResolvedPrincipal resolved = current.orElseThrow(() ->
                new IllegalArgumentException("current session is unavailable for rotation"));
        IssuedSession replacement = issue(resolved.principalRef(), rotatedAt);
        revoke(resolved.principalRef().tenantId(), resolved.sessionId(), rotatedAt);
        return replacement;
    }

    @Override
    public void revoke(TenantId tenantId, ResourceId sessionId, Instant revokedAt) {
        java.util.Objects.requireNonNull(tenantId, "tenantId");
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(revokedAt, "revokedAt");
        jdbc.update("""
                update identity.web_session set revoked_at = :revokedAt
                 where tenant_id = :tenantId and session_id = :sessionId and revoked_at is null
                """, new MapSqlParameterSource().addValue("revokedAt",
                        com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport.writeInstant(revokedAt))
                .addValue("tenantId", tenantId.value()).addValue("sessionId", sessionId.value()));
    }

    private String randomToken() {
        byte[] value = new byte[TOKEN_BYTES];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String tokenHash(String token) {
        return tokens.hash("web-session-token-v1", token);
    }

    private String csrfHash(String token) {
        return tokens.hash("web-session-csrf-v1", token);
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static Duration positive(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record SessionRow(TenantId tenantId, ResourceId sessionId, UserId userId,
                              MembershipRole role, Instant expiresAt) { }
}
