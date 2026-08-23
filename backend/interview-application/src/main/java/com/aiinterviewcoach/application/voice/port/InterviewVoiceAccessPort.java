package com.aiinterviewcoach.application.voice.port;

import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Optional;

/** Interview owner 暴露的最小快照，Voice 不读取 InterviewRepository。 */
@FunctionalInterface
public interface InterviewVoiceAccessPort {

    Access inspect(TenantId tenantId, UserId userId, ResourceId sessionId, ResourceId turnId);

    record Access(boolean allowed, String sessionState, AggregateVersion sessionVersion,
                  Optional<String> rejectionCode) {
        public Access {
            sessionState = DomainPreconditions.requireText(sessionState, "sessionState");
            DomainPreconditions.requireNonNull(sessionVersion, "sessionVersion");
            rejectionCode = rejectionCode == null ? Optional.empty() : rejectionCode;
            DomainPreconditions.require(allowed != rejectionCode.isPresent(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "interview voice access decision is inconsistent");
        }
    }
}
