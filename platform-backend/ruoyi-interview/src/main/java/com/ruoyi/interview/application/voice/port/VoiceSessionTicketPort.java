package com.ruoyi.interview.application.voice.port;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 签发并原子消费同源 WebSocket 首帧 ticket；实现不得把 ticket 写入日志。 */
@FunctionalInterface
public interface VoiceSessionTicketPort {

    Handle issue(IssueRequest request);

    /**
     * 只允许已经通过 RuoYi SecurityContext 的主体消费。
     * ticket 必须同时绑定 tenant、user、session 和 socket generation，成功后只能使用一次。
     */
    default Optional<ConsumedTicket> consume(
            String token,
            TenantId authenticatedTenantId,
            ResourceId requestedSessionId,
            UserId authenticatedUserId,
            long requestedGeneration
    ) {
        return Optional.empty();
    }

    /** 保留旧调用方的缺省重载；新代码必须提供 generation。 */
    @Deprecated
    default Optional<ConsumedTicket> consume(String token, ResourceId requestedSessionId,
                                               UserId authenticatedUserId) {
        return Optional.empty();
    }

    @Deprecated
    default Optional<ConsumedTicket> consume(String token, TenantId authenticatedTenantId,
                                               ResourceId requestedSessionId,
                                               UserId authenticatedUserId) {
        return Optional.empty();
    }

    record IssueRequest(TenantId tenantId, UserId userId, ResourceId sessionId,
                        ResourceId turnId, ResourceId executionId, ResourceId artifactId,
                        long socketGeneration, String codec, Instant expiresAt) {
        public IssueRequest {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(sessionId, "sessionId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            DomainPreconditions.requireNonNull(executionId, "executionId");
            DomainPreconditions.requireNonNull(artifactId, "artifactId");
            DomainPreconditions.require(socketGeneration > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "socket generation must be positive");
            codec = DomainPreconditions.requireText(codec, "voiceCodec");
            DomainPreconditions.requireNonNull(expiresAt, "voiceTicketExpiresAt");
        }
    }

    record Handle(ResourceId voiceSessionId, String websocketPath, String socketTicket,
                  Instant expiresAt, String codec) {
        public Handle {
            DomainPreconditions.requireNonNull(voiceSessionId, "voiceSessionId");
            websocketPath = DomainPreconditions.requireText(websocketPath, "websocketPath");
            DomainPreconditions.require(websocketPath.startsWith("/ws/v1/interviews/"),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "voice websocket path is outside the approved namespace");
            socketTicket = DomainPreconditions.requireText(socketTicket, "socketTicket");
            DomainPreconditions.require(socketTicket.length() >= 32 && socketTicket.length() <= 4096,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "voice socket ticket length is outside the approved contract");
            DomainPreconditions.requireNonNull(expiresAt, "voiceTicketExpiresAt");
            codec = DomainPreconditions.requireText(codec, "voiceCodec");
        }

        @Override
        public String toString() {
            return "Handle[voiceSessionId=" + voiceSessionId + ", websocketPath=" + websocketPath
                    + ", socketTicket=<redacted>, expiresAt=" + expiresAt + ", codec=" + codec + "]";
        }
    }

    /** 消费后的非秘密业务上下文；不包含原始 socket ticket。 */
    record ConsumedTicket(ResourceId voiceSessionId, IssueRequest request) {
        public ConsumedTicket {
            DomainPreconditions.requireNonNull(voiceSessionId, "voiceSessionId");
            DomainPreconditions.requireNonNull(request, "voiceTicketRequest");
        }
    }
}
