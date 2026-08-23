package com.ruoyi.interview.infrastructure.voice;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ResourceId;
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
    public Optional<Ticket> consume(String token, ResourceId requestedSessionId,
                                    UserId authenticatedUserId) {
        if (token == null || token.isBlank()) return Optional.empty();
        if (authenticatedUserId == null) return Optional.empty();
        Ticket ticket = tickets.remove(token);
        if (ticket == null || ticket.request().expiresAt().isBefore(clock.instant())
                || !ticket.request().sessionId().equals(requestedSessionId)
                || !ticket.request().userId().equals(authenticatedUserId)) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    private void purgeExpired() {
        var now = clock.instant();
        tickets.entrySet().removeIf(entry -> entry.getValue().request().expiresAt().isBefore(now));
    }

    public record Ticket(ResourceId voiceSessionId, IssueRequest request) { }
}

