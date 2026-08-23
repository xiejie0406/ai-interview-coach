package com.ruoyi.interview.application.voice.port;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;

/** 签发同源 WebSocket 首帧 ticket；实现必须本地完成且不得把 ticket 写入日志。 */
@FunctionalInterface
public interface VoiceSessionTicketPort {

    Handle issue(IssueRequest request);

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
}
