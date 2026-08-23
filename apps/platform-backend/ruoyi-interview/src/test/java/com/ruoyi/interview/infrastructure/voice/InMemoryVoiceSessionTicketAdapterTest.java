package com.ruoyi.interview.infrastructure.voice;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVoiceSessionTicketAdapterTest {
    @Test
    void ticketIsPrincipalBoundAndSingleUse() {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        var tickets = new InMemoryVoiceSessionTicketAdapter(Clock.fixed(now, ZoneOffset.UTC));
        var request = new VoiceSessionTicketPort.IssueRequest(
                TenantId.of("tenant-a"), UserId.of("42"), ResourceId.of("session-a"),
                ResourceId.of("turn-a"), ResourceId.of("execution-a"),
                ResourceId.of("artifact-a"), 1, "audio/webm;codecs=opus", now.plusSeconds(60));

        var wrongPrincipal = tickets.issue(request);
        assertTrue(tickets.consume(wrongPrincipal.socketTicket(), request.sessionId(), UserId.of("43")).isEmpty());

        var handle = tickets.issue(request);
        assertTrue(tickets.consume(handle.socketTicket(), request.sessionId(), request.userId()).isPresent());
        assertTrue(tickets.consume(handle.socketTicket(), request.sessionId(), request.userId()).isEmpty());
    }
}
