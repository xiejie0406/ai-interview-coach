package com.ruoyi.interview.infrastructure.voice;

import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;

import java.util.Optional;

/** 生产选择 Redis 但连接工厂不存在时的 fail-closed 适配器。 */
public final class UnavailableVoiceSessionTicketAdapter implements VoiceSessionTicketPort {
    @Override
    public Handle issue(IssueRequest request) {
        throw unavailable();
    }

    @Override
    public Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                            ResourceId requestedSessionId,
                                            UserId authenticatedUserId,
                                            long requestedGeneration) {
        throw unavailable();
    }

    private static AdapterUnavailableException unavailable() {
        return new AdapterUnavailableException("voice-session-ticket");
    }
}
