package com.ruoyi.interview.application.voice.port;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

/** 已批准配置、地域、Provider 健康和 Feature Flag 折叠后的只读能力。 */
@FunctionalInterface
public interface VoiceCapabilityPort {

    Capability current(TenantId tenantId, UserId userId);

    record Capability(boolean enabled, List<String> supportedCodecs,
                      int maximumDurationSeconds, long maximumBytes,
                      int maximumInFlightChunks, long maximumChunkBytes,
                      long maximumBufferedDurationMillis,
                      Optional<String> unavailableReasonCode) {
        public Capability {
            supportedCodecs = List.copyOf(supportedCodecs == null ? List.of() : supportedCodecs);
            supportedCodecs.forEach(codec -> DomainPreconditions.requireText(codec, "supportedCodec"));
            DomainPreconditions.require(maximumDurationSeconds > 0 && maximumBytes > 0
                            && maximumInFlightChunks > 0 && maximumChunkBytes > 0
                            && maximumBufferedDurationMillis > 0,
                    DomainErrorCode.INVALID_ARGUMENT, "voice limits must be positive");
            DomainPreconditions.require(maximumChunkBytes <= maximumBytes
                            && maximumBufferedDurationMillis <= maximumDurationSeconds * 1000L,
                    DomainErrorCode.INVALID_ARGUMENT, "voice flow limits exceed session limits");
            unavailableReasonCode = unavailableReasonCode == null
                    ? Optional.empty() : unavailableReasonCode;
            DomainPreconditions.require(enabled != unavailableReasonCode.isPresent(),
                    DomainErrorCode.INVALID_ARGUMENT, "voice capability decision is inconsistent");
            DomainPreconditions.require(!enabled || !supportedCodecs.isEmpty(),
                    DomainErrorCode.INVALID_ARGUMENT, "enabled voice capability requires a codec");
        }
    }
}
