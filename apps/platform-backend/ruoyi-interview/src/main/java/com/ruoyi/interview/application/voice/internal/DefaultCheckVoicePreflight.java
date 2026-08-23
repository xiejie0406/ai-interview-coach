package com.ruoyi.interview.application.voice.internal;

import com.ruoyi.interview.application.governance.port.ConsentQueryPort;
import com.ruoyi.interview.application.voice.CheckVoicePreflight;
import com.ruoyi.interview.application.voice.port.InterviewVoiceAccessPort;
import com.ruoyi.interview.application.voice.port.VoiceCapabilityPort;
import com.ruoyi.interview.domain.governance.ConsentPurpose;

import java.util.List;

/** Preflight 只读策略；任何拒绝都发生在 Artifact、录音上传和 Provider 调用之前。 */
public final class DefaultCheckVoicePreflight implements CheckVoicePreflight {

    private final ConsentQueryPort consentQuery;
    private final VoiceCapabilityPort capabilityPort;
    private final InterviewVoiceAccessPort interviewAccess;

    public DefaultCheckVoicePreflight(ConsentQueryPort consentQuery,
                                      VoiceCapabilityPort capabilityPort,
                                      InterviewVoiceAccessPort interviewAccess) {
        this.consentQuery = java.util.Objects.requireNonNull(consentQuery);
        this.capabilityPort = java.util.Objects.requireNonNull(capabilityPort);
        this.interviewAccess = java.util.Objects.requireNonNull(interviewAccess);
    }

    @Override
    public Result handle(Query query) {
        var principal = query.context().principal();
        var access = interviewAccess.inspect(principal.tenantId(), principal.userId(),
                query.interviewId(), query.turnId());
        var capability = capabilityPort.current(principal.tenantId(), principal.userId());
        boolean voiceConsent = consentQuery.current(principal.tenantId(), principal.userId(),
                ConsentPurpose.VOICE_CAPTURE, query.context().requestedAt()).granted();
        boolean modelConsent = consentQuery.current(principal.tenantId(), principal.userId(),
                ConsentPurpose.MODEL_PROCESSING, query.context().requestedAt()).granted();
        boolean consentRequired = !voiceConsent || !modelConsent;
        List<String> supported = query.codecCandidates().stream()
                .distinct()
                .filter(capability.supportedCodecs()::contains)
                .toList();
        boolean enabled = access.allowed() && capability.enabled() && !consentRequired && !supported.isEmpty();
        var reason = !access.allowed()
                ? access.rejectionCode()
                : !capability.enabled()
                    ? capability.unavailableReasonCode()
                    : supported.isEmpty()
                        ? java.util.Optional.of("UNSUPPORTED_CODEC")
                        : java.util.Optional.<String>empty();
        return new Result(enabled, consentRequired, supported,
                capability.maximumDurationSeconds(), capability.maximumBytes(), reason);
    }
}
