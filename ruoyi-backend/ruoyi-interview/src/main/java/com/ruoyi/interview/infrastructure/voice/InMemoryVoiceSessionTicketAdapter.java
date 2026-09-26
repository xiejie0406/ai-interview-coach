package com.ruoyi.interview.infrastructure.voice;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例本地运行使用的一次性 Voice WebSocket ticket。
 * ticket 只保存在进程内存中，消费后立即删除，且不会进入日志或 URL。
 */
public final class InMemoryVoiceSessionTicketAdapter implements VoiceSessionTicketPort {
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryVoiceSessionTicketAdapter(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public Handle issue(IssueRequest request) {
        purgeExpired();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        ResourceId voiceSessionId = ResourceId.of(java.util.UUID.randomUUID());
        Ticket ticket = new Ticket(voiceSessionId, request);
        if (tickets.putIfAbsent(token, ticket) != null) {
            return issue(request);
        }
        return new Handle(voiceSessionId,
                "/ws/v1/interviews/" + request.sessionId().value() + "/voice",
                token, request.expiresAt(), request.codec());
    }

    /**
     * 仅允许已经通过 RuoYi SecurityContext 的主体消费；ticket 只是 turn 绑定，
     * 不能替代 RuoYi JWT，也不能跨用户重放。
     */
    @Override
    public Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                            ResourceId requestedSessionId,
                                            UserId authenticatedUserId,
                                            long requestedGeneration) {
        if (token == null || token.isBlank()) return Optional.empty();
        if (authenticatedTenantId == null || authenticatedUserId == null
                || requestedSessionId == null || requestedGeneration <= 0) return Optional.empty();
        Ticket ticket = tickets.get(token);
        if (ticket == null || !ticket.request().expiresAt().isAfter(clock.instant())
                || !ticket.request().tenantId().equals(authenticatedTenantId)
                || !ticket.request().sessionId().equals(requestedSessionId)
                || !ticket.request().userId().equals(authenticatedUserId)
                || ticket.request().socketGeneration() != requestedGeneration) {
            return Optional.empty();
        }
        return tickets.remove(token, ticket)
                ? Optional.of(new ConsumedTicket(ticket.voiceSessionId(), ticket.request()))
                : Optional.empty();
    }

    @Override
    @Deprecated
    public Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                            ResourceId requestedSessionId,
                                            UserId authenticatedUserId) {
        Ticket ticket = token == null ? null : tickets.get(token);
        if (ticket == null || authenticatedTenantId == null) return Optional.empty();
        return consume(token, authenticatedTenantId, requestedSessionId, authenticatedUserId,
                ticket.request().socketGeneration());
    }

    /** 仅供旧的定向单元测试过渡；生产 WebSocket 必须调用完整主体绑定契约。 */
    public Optional<ConsumedTicket> consume(String token, ResourceId requestedSessionId,
                                            UserId authenticatedUserId) {
        Ticket ticket = token == null ? null : tickets.get(token);
        if (ticket == null) return Optional.empty();
        return consume(token, ticket.request().tenantId(), requestedSessionId,
                authenticatedUserId, ticket.request().socketGeneration());
    }

    private void purgeExpired() {
        var now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().request().expiresAt().isAfter(now));
    }

    public record Ticket(ResourceId voiceSessionId, IssueRequest request) { }
}

