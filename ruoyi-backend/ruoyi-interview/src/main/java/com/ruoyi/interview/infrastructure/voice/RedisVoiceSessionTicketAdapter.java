package com.ruoyi.interview.infrastructure.voice;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 多实例运行时的一次性 Voice WebSocket ticket，通过 Redis Lua compare-and-delete 原子消费。 */
public final class RedisVoiceSessionTicketAdapter implements VoiceSessionTicketPort {
    private static final String DEFAULT_PREFIX = "interview:voice:ticket:v1:";
    /**
     * 只有 ticket 中的主体绑定字段全部匹配当前已认证主体，才会在同一个 Redis
     * 脚本执行中删除。错误主体尝试不会消耗合法 ticket，也不会把 ticket 内容返回给调用方。
     */
    private static final DefaultRedisScript<String> CONSUME_IF_BOUND = new DefaultRedisScript<>("""
            local encoded = redis.call('GET', KEYS[1])
            if not encoded then return nil end
            local ok, ticket = pcall(cjson.decode, encoded)
            if not ok or type(ticket) ~= 'table' then return nil end
            if tostring(ticket.tenantId) ~= ARGV[1]
                    or tostring(ticket.userId) ~= ARGV[2]
                    or tostring(ticket.sessionId) ~= ARGV[3]
                    or tostring(ticket.socketGeneration) ~= ARGV[4] then
                return nil
            end
            redis.call('DEL', KEYS[1])
            return encoded
            """, String.class);
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    private final String keyPrefix;

    public RedisVoiceSessionTicketAdapter(RedisConnectionFactory connectionFactory,
                                          ObjectMapper json, Clock clock, String keyPrefix) {
        this.redis = new StringRedisTemplate(Objects.requireNonNull(connectionFactory, "connectionFactory"));
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keyPrefix = normalizedPrefix(keyPrefix);
    }

    @Override
    public Handle issue(IssueRequest request) {
        Objects.requireNonNull(request, "request");
        Duration ttl = Duration.between(clock.instant(), request.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("voice ticket expiry must be in the future");
        }
        ResourceId voiceSessionId = ResourceId.of(UUID.randomUUID());
        StoredTicket stored = StoredTicket.from(voiceSessionId, request);
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                String token = token();
                Boolean created = redis.opsForValue().setIfAbsent(key(token),
                        json.writeValueAsString(stored), ttl);
                if (Boolean.TRUE.equals(created)) {
                    return new Handle(voiceSessionId,
                            "/ws/v1/interviews/" + request.sessionId().value() + "/voice",
                            token, request.expiresAt(), request.codec());
                }
            }
            throw unavailable();
        } catch (JacksonException | org.springframework.dao.DataAccessException exception) {
            throw unavailable();
        }
    }

    @Override
    public Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                            ResourceId requestedSessionId,
                                            UserId authenticatedUserId,
                                            long requestedGeneration) {
        if (token == null || token.isBlank() || token.length() > 4096
                || !token.matches("[A-Za-z0-9_-]{32,4096}")
                || authenticatedTenantId == null || requestedSessionId == null
                || authenticatedUserId == null || requestedGeneration <= 0) {
            return Optional.empty();
        }
        final String encoded;
        try {
            encoded = redis.execute(CONSUME_IF_BOUND, List.of(key(token)),
                    authenticatedTenantId.value(), authenticatedUserId.value(),
                    requestedSessionId.value(), Long.toString(requestedGeneration));
        } catch (org.springframework.dao.DataAccessException exception) {
            throw unavailable();
        }
        if (encoded == null) return Optional.empty();
        try {
            StoredTicket stored = json.readValue(encoded, StoredTicket.class);
            if (!stored.expiresAt().isAfter(clock.instant())
                    || !stored.tenantId().equals(authenticatedTenantId.value())
                    || !stored.userId().equals(authenticatedUserId.value())
                    || !stored.sessionId().equals(requestedSessionId.value())
                    || stored.socketGeneration() != requestedGeneration) {
                return Optional.empty();
            }
            return Optional.of(stored.toConsumedTicket());
        } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Deprecated
    public Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                            ResourceId requestedSessionId,
                                            UserId authenticatedUserId) {
        // 旧调用方没有 generation，不应在生产路径中绕过该约束。
        return Optional.empty();
    }

    private String token() {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private String key(String token) {
        return keyPrefix + token;
    }

    private static String normalizedPrefix(String configured) {
        if (configured == null || configured.isBlank()) return DEFAULT_PREFIX;
        String value = configured.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9:_-]+")) {
            throw new IllegalArgumentException("voice ticket Redis key prefix is invalid");
        }
        return value.endsWith(":") ? value : value + ":";
    }

    private static AdapterUnavailableException unavailable() {
        return new AdapterUnavailableException("voice-session-ticket");
    }

    /** Redis value 仅包含恢复必需白名单字段，不包含 ticket、JWT、音频或正文。 */
    public record StoredTicket(String voiceSessionId, String tenantId, String userId,
                               String sessionId, String turnId, String executionId,
                               String artifactId, long socketGeneration, String codec,
                               Instant expiresAt) {
        static StoredTicket from(ResourceId voiceSessionId, IssueRequest request) {
            return new StoredTicket(voiceSessionId.value(), request.tenantId().value(),
                    request.userId().value(), request.sessionId().value(), request.turnId().value(),
                    request.executionId().value(), request.artifactId().value(),
                    request.socketGeneration(), request.codec(), request.expiresAt());
        }

        ConsumedTicket toConsumedTicket() {
            IssueRequest request = new IssueRequest(TenantId.of(tenantId), UserId.of(userId),
                    ResourceId.of(sessionId), ResourceId.of(turnId), ResourceId.of(executionId),
                    ResourceId.of(artifactId), socketGeneration, codec, expiresAt);
            return new ConsumedTicket(ResourceId.of(voiceSessionId), request);
        }
    }
}
