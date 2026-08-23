package com.aiinterviewcoach.adapters.inbound.rest.identity;

import com.aiinterviewcoach.application.identity.port.RegistrationPolicyPort;
import com.aiinterviewcoach.adapters.inbound.rest.voice.VoicePolicyCatalog;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Public, server-authoritative policy references needed before registration. */
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "identity-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/policies", produces = MediaType.APPLICATION_JSON_VALUE)
public final class RegistrationPolicyController {

    private final RegistrationPolicyPort policies;
    private final Clock clock;

    public RegistrationPolicyController(RegistrationPolicyPort policies, Clock clock) {
        this.policies = java.util.Objects.requireNonNull(policies);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @GetMapping("/current")
    public ResponseEntity<CurrentPoliciesResponse> current() {
        Map<ConsentPurpose, ImmutableVersionRef> required = policies.requiredPolicies("zh-CN", clock.instant());
        java.util.LinkedHashMap<ConsentPurpose, ImmutableVersionRef> all = new java.util.LinkedHashMap<>(required);
        all.putAll(VoicePolicyCatalog.POLICIES);
        List<PolicyResponse> items = all.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> policy(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CurrentPoliciesResponse("identity-registration-v1", items));
    }

    private static PolicyResponse policy(ConsentPurpose purpose, ImmutableVersionRef version) {
        return switch (purpose) {
            case SERVICE_TERMS -> new PolicyResponse(purpose, version.resourceId().value(),
                    "服务条款", "使用 AI Interview Coach 前需要同意服务条款。", "/privacy/terms", true, false);
            case PRIVACY_NOTICE -> new PolicyResponse(purpose, version.resourceId().value(),
                    "隐私说明", "注册所需的账号、会话和训练数据处理说明。", "/privacy", true, true);
            case VOICE_CAPTURE -> new PolicyResponse(purpose, version.resourceId().value(),
                    "语音采集说明", "仅在语音回答时采集本轮音频，可随时撤回并改用文字。",
                    "/privacy/voice", false, true);
            case MODEL_PROCESSING -> new PolicyResponse(purpose, version.resourceId().value(),
                    "语音模型处理说明", "授权语音识别服务处理本轮音频并生成可修正转写。",
                    "/privacy/model-processing", false, true);
        };
    }

    public record CurrentPoliciesResponse(String policySetVersion, List<PolicyResponse> policies) { }

    public record PolicyResponse(ConsentPurpose purpose, String versionId, String title, String summary,
                                 String documentUrl, boolean requiredForRegistration, boolean revocable) { }
}
